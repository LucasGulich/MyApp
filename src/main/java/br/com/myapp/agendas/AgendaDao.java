package br.com.myapp.agendas;

import br.com.myapp.core.Log;
import br.com.myapp.core.Texto;
import br.com.myapp.data.Database;
import br.com.myapp.security.CryptoService;
import br.com.myapp.security.SecurityService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso ao banco para as agendas externas.
 *
 * Duas colunas pedem cuidado:
 *
 *  - {@code link}: cifrado <b>sempre</b>, como a senha de uma credencial. Por
 *    isso gravar exige o aplicativo destrancado, e as listagens devolvem a
 *    agenda sem o link — quem precisa dele pede por {@link #lerLink}.
 *  - {@code conteudo}: o último .ics baixado. Em claro, como o título de um
 *    lembrete comum; cifrado quando a agenda é marcada como protegida.
 */
public class AgendaDao {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    // ------------------------------------------------------------- escrita

    public Agenda salvar(Agenda agenda) {
        return agenda.getId() == null ? inserir(agenda) : atualizar(agenda);
    }

    private Agenda inserir(Agenda a) {
        String sql = """
                INSERT INTO agenda (nome, cor, link, ocultar_recusados, protegida,
                                    criado_em, atualizado_em, antecedencias, som_ativo,
                                    avisar_dia_inteiro, hora_dia_inteiro)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            a.setAtualizadoEm(LocalDateTime.now());
            ps.setString(1, a.getNome());
            ps.setString(2, a.getCor());
            ps.setString(3, cifrar(a.getLink()));
            ps.setInt(4, a.isOcultarRecusados() ? 1 : 0);
            ps.setInt(5, a.isProtegida() ? 1 : 0);
            ps.setLong(6, paraMillis(a.getCriadoEm()));
            ps.setLong(7, paraMillis(a.getAtualizadoEm()));
            ps.setString(8, a.antecedenciasCsv());
            ps.setInt(9, a.isSomAtivo() ? 1 : 0);
            ps.setInt(10, a.isAvisarDiaInteiro() ? 1 : 0);
            ps.setString(11, HORA.format(a.getHoraDiaInteiro()));
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    a.setId(chaves.getLong(1));
                }
            }
            Log.info("Agenda conectada: id=" + a.getId());
            return a;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar a agenda", e);
        }
    }

    /**
     * Altera nome, cor, link e opções.
     *
     * Trocar a proteção muda a forma de guardar o conteúdo baixado; em vez de
     * converter, o conteúdo é descartado e volta na próxima sincronização.
     */
    private Agenda atualizar(Agenda a) {
        boolean protecaoMudou = porId(a.getId())
                .map(antes -> antes.isProtegida() != a.isProtegida())
                .orElse(false);

        String sql = """
                UPDATE agenda SET nome = ?, cor = ?, link = ?, ocultar_recusados = ?,
                                  protegida = ?, atualizado_em = ?, antecedencias = ?,
                                  som_ativo = ?, avisar_dia_inteiro = ?, hora_dia_inteiro = ?
                """ + (protecaoMudou ? ", conteudo = NULL, sincronizada_em = NULL" : "")
                + " WHERE id = ?";
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            a.setAtualizadoEm(LocalDateTime.now());
            ps.setString(1, a.getNome());
            ps.setString(2, a.getCor());
            ps.setString(3, cifrar(a.getLink()));
            ps.setInt(4, a.isOcultarRecusados() ? 1 : 0);
            ps.setInt(5, a.isProtegida() ? 1 : 0);
            ps.setLong(6, paraMillis(a.getAtualizadoEm()));
            ps.setString(7, a.antecedenciasCsv());
            ps.setInt(8, a.isSomAtivo() ? 1 : 0);
            ps.setInt(9, a.isAvisarDiaInteiro() ? 1 : 0);
            ps.setString(10, HORA.format(a.getHoraDiaInteiro()));
            ps.setLong(11, a.getId());
            ps.executeUpdate();
            Log.info("Agenda alterada: id=" + a.getId());
            return a;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao alterar a agenda", e);
        }
    }

    /** Guarda o que acabou de ser baixado e limpa o erro anterior. */
    public void gravarSincronizacao(long id, String conteudo, boolean protegida, LocalDateTime quando) {
        String guardado = protegida ? cifrar(conteudo) : conteudo;
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE agenda SET conteudo = ?, sincronizada_em = ?, ultimo_erro = NULL WHERE id = ?")) {
            ps.setString(1, guardado);
            ps.setLong(2, paraMillis(quando));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar a sincronização da agenda " + id, e);
        }
    }

    /**
     * Marca a hora da sincronização sem trocar o conteúdo guardado.
     *
     * Caso da agenda protegida baixada com o aplicativo trancado: sem a chave,
     * o conteúdo novo não tem como ser cifrado para o banco e vale só na
     * memória até o próximo destrancar.
     */
    public void marcarSincronizada(long id, LocalDateTime quando) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE agenda SET sincronizada_em = ?, ultimo_erro = NULL WHERE id = ?")) {
            ps.setLong(1, paraMillis(quando));
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Falha ao marcar a sincronização da agenda " + id + ": " + e.getMessage());
        }
    }

    /**
     * Registra a falha da última tentativa. O conteúdo anterior fica: a tela
     * continua mostrando a última cópia boa.
     */
    public void gravarErro(long id, String erro) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE agenda SET ultimo_erro = ? WHERE id = ?")) {
            if (erro == null) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, erro);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Falha ao registrar o erro da agenda " + id + ": " + e.getMessage());
        }
    }

    /** Exclusão lógica: a agenda some da tela e para de sincronizar. */
    public void excluir(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE agenda SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            long agora = Instant.now().toEpochMilli();
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
            Log.info("Agenda excluida (logicamente): id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir a agenda", e);
        }
    }

    // -------------------------------------------------------------- leitura

    /** Agendas vivas, sem o link. */
    public List<Agenda> listar() {
        List<Agenda> lista = new ArrayList<>();
        String sql = "SELECT * FROM agenda WHERE data_exclusao IS NULL ORDER BY nome COLLATE NOCASE, id";
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar as agendas", e);
        }
        return lista;
    }

    public Optional<Agenda> porId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM agenda WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a agenda", e);
        }
    }

    /**
     * O endereço secreto, decifrado.
     *
     * @return vazio com o aplicativo trancado
     */
    public Optional<String> lerLink(long id) {
        return lerColuna(id, "link").flatMap(this::decifrar);
    }

    /**
     * O último .ics baixado.
     *
     * @return vazio se nunca sincronizou, ou se a agenda é protegida e o
     *         aplicativo está trancado
     */
    public Optional<String> lerConteudo(long id) {
        return lerColuna(id, "conteudo").flatMap(guardado ->
                CryptoService.estaCifrado(guardado) ? decifrar(guardado) : Optional.of(guardado));
    }

    private Optional<String> lerColuna(long id, String coluna) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT " + coluna + " FROM agenda WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler a agenda " + id, e);
        }
    }

    private Agenda montar(ResultSet rs) throws SQLException {
        Agenda a = new Agenda();
        a.setId(rs.getLong("id"));
        a.setNome(rs.getString("nome"));
        a.setCor(rs.getString("cor"));
        a.setUltimoErro(rs.getString("ultimo_erro"));
        a.setOcultarRecusados(rs.getInt("ocultar_recusados") == 1);
        a.setProtegida(rs.getInt("protegida") == 1);
        a.antecedenciasCsv(rs.getString("antecedencias"));
        a.setSomAtivo(rs.getInt("som_ativo") == 1);
        a.setAvisarDiaInteiro(rs.getInt("avisar_dia_inteiro") == 1);
        a.setHoraDiaInteiro(Texto.interpretarHora(rs.getString("hora_dia_inteiro"),
                AvisosDeAgenda.HORA_PADRAO_DIA_INTEIRO));
        a.setCriadoEm(paraData(rs.getLong("criado_em")));
        a.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long sincronizada = rs.getLong("sincronizada_em");
        a.setSincronizadaEm(rs.wasNull() ? null : paraData(sincronizada));
        return a;
    }

    // ------------------------------------------------------------ cifragem

    private String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        if (!SecurityService.estaDestrancado()) {
            throw new IllegalStateException("Destranque o aplicativo para gravar a agenda.");
        }
        return CryptoService.cifrarTexto(texto, SecurityService.chave());
    }

    private Optional<String> decifrar(String guardado) {
        if (!SecurityService.estaDestrancado()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(CryptoService.decifrarTexto(guardado, SecurityService.chave()));
        } catch (Exception e) {
            Log.aviso("Não foi possível decifrar um dado da agenda.");
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ conversão

    private static long paraMillis(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime paraData(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
