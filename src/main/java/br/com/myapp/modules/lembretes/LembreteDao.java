package br.com.myapp.modules.lembretes;

import br.com.myapp.core.Log;
import br.com.myapp.data.Database;
import br.com.myapp.security.CryptoService;
import br.com.myapp.security.SecurityService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso ao banco para os lembretes.
 *
 * Um lembrete marcado como protegido tem título e descrição gravados
 * cifrados. Tudo o que e necessário para o agendador funcionar (datas,
 * Recorrência, antecedências) fica em claro de propósito: assim os avisos
 * continuam saindo no horário mesmo com o aplicativo trancado, e o alerta
 * simplesmente omite o conteúdo até você destrancar.
 */
public class LembreteDao {

    /** Texto exibido no lugar do conteúdo quando o app esta trancado. */
    public static final String PROTEGIDO = "Conteúdo protegido";

    // ------------------------------------------------------------- escrita

    public Lembrete salvar(Lembrete lembrete) {
        return lembrete.getId() == null ? inserir(lembrete) : atualizar(lembrete);
    }

    private Lembrete inserir(Lembrete l) {
        String sql = """
                INSERT INTO lembrete (titulo, descricao, sensivel, tipo_recorrencia, inicio, fim,
                                      dias_semana, dia_mes, intervalo_minutos, antecedencias,
                                      ativo, cor, acao, criado_em, atualizado_em,
                                      som_ativo, data_exclusao, ordem)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            l.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, l);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    l.setId(chaves.getLong(1));
                }
            }
            Log.info("Lembrete criado: id=" + l.getId());
            return l;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o lembrete", e);
        }
    }

    private Lembrete atualizar(Lembrete l) {
        String sql = """
                UPDATE lembrete SET titulo = ?, descricao = ?, sensivel = ?, tipo_recorrencia = ?,
                                    inicio = ?, fim = ?, dias_semana = ?, dia_mes = ?,
                                    intervalo_minutos = ?, antecedencias = ?, ativo = ?, cor = ?,
                                    acao = ?, criado_em = ?, atualizado_em = ?,
                                    som_ativo = ?, data_exclusao = ?, ordem = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            l.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, l);
            ps.setLong(19, l.getId());
            ps.executeUpdate();
            Log.info("Lembrete alterado: id=" + l.getId());
            return l;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao alterar o lembrete", e);
        }
    }

    private void preencher(PreparedStatement ps, Lembrete l) throws SQLException {
        ps.setString(1, protegerSeNecessario(l.getTitulo(), l.isSensivel()));
        ps.setString(2, protegerSeNecessario(l.getDescricao(), l.isSensivel()));
        ps.setInt(3, l.isSensivel() ? 1 : 0);
        ps.setString(4, l.getTipo().name());
        ps.setLong(5, paraMillis(l.getInicio()));
        if (l.getFim() == null) {
            ps.setNull(6, java.sql.Types.INTEGER);
        } else {
            ps.setLong(6, paraMillis(l.getFim()));
        }
        ps.setString(7, l.diasSemanaCsv());
        if (l.getDiaMes() == null) {
            ps.setNull(8, java.sql.Types.INTEGER);
        } else {
            ps.setInt(8, l.getDiaMes());
        }
        if (l.getIntervaloMinutos() == null) {
            ps.setNull(9, java.sql.Types.INTEGER);
        } else {
            ps.setInt(9, l.getIntervaloMinutos());
        }
        ps.setString(10, l.antecedenciasCsv());
        ps.setInt(11, l.isAtivo() ? 1 : 0);
        ps.setString(12, l.getCor());
        ps.setString(13, l.getAcao());
        ps.setLong(14, paraMillis(l.getCriadoEm()));
        ps.setLong(15, paraMillis(l.getAtualizadoEm()));
        ps.setInt(16, l.isSomAtivo() ? 1 : 0);
        if (l.getDataExclusao() == null) {
            ps.setNull(17, java.sql.Types.INTEGER);
        } else {
            ps.setLong(17, paraMillis(l.getDataExclusao()));
        }
        ps.setInt(18, l.getOrdem());
    }

    /**
     * Exclusão lógica: grava a data em vez de apagar a linha.
     *
     * Nada sai do banco. O lembrete some das telas, para de alertar e pode ser
     * restaurado a qualquer momento. Um `DELETE` de verdade não existe no
     * aplicativo — informação apagada por engano não volta.
     */
    public void excluir(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE lembrete SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            long agora = Instant.now().toEpochMilli();
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
            Log.info("Lembrete excluido (logicamente): id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir o lembrete", e);
        }
    }

    /** Desfaz a exclusão lógica. */
    public void restaurar(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE lembrete SET data_exclusao = NULL, atualizado_em = ? WHERE id = ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, id);
            ps.executeUpdate();
            Log.info("Lembrete restaurado: id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao restaurar o lembrete", e);
        }
    }

    /**
     * Grava a ordem manual de todos de uma vez.
     *
     * Arrastar um lembrete muda a posição dos demais; gravar um por vez
     * deixaria a lista inconsistente se algo falhasse no meio.
     */
    public void gravarOrdem(List<Lembrete> naNovaOrdem) {
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE lembrete SET ordem = ?, atualizado_em = ? WHERE id = ?")) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (Lembrete l : naNovaOrdem) {
                    l.setOrdem(ordem);
                    ps.setInt(1, ordem++);
                    ps.setLong(2, agora);
                    ps.setLong(3, l.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            Database.conexao().commit();
        } catch (SQLException e) {
            try {
                Database.conexao().rollback();
            } catch (SQLException ignorado) {
                // O erro original e o que importa.
            }
            throw new IllegalStateException("Falha ao gravar a ordem dos lembretes", e);
        } finally {
            try {
                Database.conexao().setAutoCommit(true);
            } catch (SQLException ignorado) {
                // Idem.
            }
        }
    }

    /** Algum lembrete ja foi arrastado? So entao a ordem manual vale. */
    public boolean temOrdemManual() {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT 1 FROM lembrete WHERE data_exclusao IS NULL AND ordem >= 0 LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void definirAtivo(long id, boolean ativo) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("UPDATE lembrete SET ativo = ?, atualizado_em = ? WHERE id = ?")) {
            ps.setInt(1, ativo ? 1 : 0);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ativar/desativar o lembrete", e);
        }
    }

    // -------------------------------------------------------------- leitura

    /**
     * Lembretes válidos, ou seja, os que não foram excluídos.
     *
     * Todas as consultas do dia a dia filtram por data_exclusao IS NULL. Quem
     * quiser ver os excluídos precisa pedir explicitamente, por
     * {@link #listarExcluidos()}.
     */
    public List<Lembrete> listarTodos() {
        return consultar("""
                SELECT * FROM lembrete
                WHERE data_exclusao IS NULL
                ORDER BY ativo DESC, inicio
                """);
    }

    public List<Lembrete> listarAtivos() {
        return consultar("""
                SELECT * FROM lembrete
                WHERE ativo = 1 AND data_exclusao IS NULL
                ORDER BY inicio
                """);
    }

    /** A lixeira: o que foi excluído, do mais recente para o mais antigo. */
    public List<Lembrete> listarExcluidos() {
        return consultar("""
                SELECT * FROM lembrete
                WHERE data_exclusao IS NOT NULL
                ORDER BY data_exclusao DESC
                """);
    }

    /**
     * Busca por identificador, incluindo os excluídos.
     *
     * Traz o excluído de propósito: a tela da lixeira precisa dele, e o
     * agendador confere o campo antes de alertar.
     */
    public Optional<Lembrete> porId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM lembrete WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar o lembrete", e);
        }
    }

    private List<Lembrete> consultar(String sql) {
        List<Lembrete> lista = new ArrayList<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar os lembretes", e);
        }
        return lista;
    }

    private Lembrete montar(ResultSet rs) throws SQLException {
        Lembrete l = new Lembrete();
        l.setId(rs.getLong("id"));
        l.setSensivel(rs.getInt("sensivel") == 1);
        l.setTitulo(revelarSePossivel(rs.getString("titulo"), l.isSensivel()));
        l.setDescricao(revelarSePossivel(rs.getString("descricao"), l.isSensivel()));
        l.setTipo(TipoRecorrencia.valueOf(rs.getString("tipo_recorrencia")));
        l.setInicio(paraData(rs.getLong("inicio")));

        long fim = rs.getLong("fim");
        l.setFim(rs.wasNull() ? null : paraData(fim));

        l.diasSemanaCsv(rs.getString("dias_semana"));

        int diaMes = rs.getInt("dia_mes");
        l.setDiaMes(rs.wasNull() ? null : diaMes);

        int intervalo = rs.getInt("intervalo_minutos");
        l.setIntervaloMinutos(rs.wasNull() ? null : intervalo);

        l.antecedenciasCsv(rs.getString("antecedencias"));
        l.setAtivo(rs.getInt("ativo") == 1);
        l.setCor(rs.getString("cor"));
        l.setAcao(rs.getString("acao"));
        l.setCriadoEm(paraData(rs.getLong("criado_em")));
        l.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));
        l.setSomAtivo(rs.getInt("som_ativo") == 1);

        long exclusao = rs.getLong("data_exclusao");
        l.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        l.setOrdem(rs.getInt("ordem"));
        return l;
    }

    // ------------------------------------------------------------- disparos

    /** Este aviso específico já foi mostrado? */
    public boolean jaDisparou(long lembreteId, LocalDateTime ocorrencia, int antecedencia) {
        String sql = "SELECT 1 FROM disparo WHERE lembrete_id = ? AND ocorrencia = ? AND antecedencia = ?";
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setLong(1, lembreteId);
            ps.setLong(2, paraMillis(ocorrencia));
            ps.setInt(3, antecedencia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Log.erro("Falha ao verificar disparo do lembrete " + lembreteId, e);
            return true; // na dúvida, não repete o alerta
        }
    }

    /**
     * Marca um aviso como disparado.
     *
     * @return true se foi registrado agora, false se já existia (evita alerta duplicado)
     */
    public boolean registrarDisparo(long lembreteId, LocalDateTime ocorrencia, int antecedencia) {
        String sql = """
                INSERT OR IGNORE INTO disparo (lembrete_id, ocorrencia, antecedencia, disparado_em)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setLong(1, lembreteId);
            ps.setLong(2, paraMillis(ocorrencia));
            ps.setInt(3, antecedencia);
            ps.setLong(4, Instant.now().toEpochMilli());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Log.erro("Falha ao registrar disparo do lembrete " + lembreteId, e);
            return false;
        }
    }

    /** Marca como visto, para o alerta sair da lista de pendentes. */
    public void reconhecer(long lembreteId, LocalDateTime ocorrencia) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE disparo SET reconhecido = 1 WHERE lembrete_id = ? AND ocorrencia = ?")) {
            ps.setLong(1, lembreteId);
            ps.setLong(2, paraMillis(ocorrencia));
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.erro("Falha ao reconhecer o lembrete " + lembreteId, e);
        }
    }

    /** Avisos que dispararam e ainda não foram vistos (o "Você perdeu isto"). */
    public List<Disparo> pendentes(int limite) {
        String sql = """
                SELECT lembrete_id, ocorrencia, antecedencia, disparado_em
                FROM disparo
                WHERE reconhecido = 0
                ORDER BY ocorrencia DESC
                LIMIT ?
                """;
        List<Disparo> lista = new ArrayList<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Disparo(
                            rs.getLong("lembrete_id"),
                            paraData(rs.getLong("ocorrencia")),
                            rs.getInt("antecedencia"),
                            paraData(rs.getLong("disparado_em"))));
                }
            }
        } catch (SQLException e) {
            Log.erro("Falha ao listar avisos pendentes", e);
        }
        return lista;
    }

    /**
     * Arquiva disparos antigos.
     *
     * Antes isto era um DELETE. Agora apenas marca a data: o histórico sai das
     * consultas do dia a dia, mas continua no banco, disponível para um
     * relatório futuro de "o que foi avisado quando".
     */
    public void limparDisparosAntigos(int diasParaManter) {
        long limite = Instant.now().minusSeconds(diasParaManter * 86_400L).toEpochMilli();
        try (PreparedStatement ps = Database.conexao().prepareStatement("""
                UPDATE disparo SET data_exclusao = ?
                WHERE reconhecido = 1 AND disparado_em < ? AND data_exclusao IS NULL
                """)) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, limite);
            int arquivados = ps.executeUpdate();
            if (arquivados > 0) {
                Log.info("Arquivamento: " + arquivados + " disparos antigos marcados.");
            }
        } catch (SQLException e) {
            Log.aviso("Falha ao arquivar disparos: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------- adiamentos

    /**
     * Existe algum adiamento deste lembrete ainda esperando a hora de voltar?
     *
     * Consultado antes de arquivar um lembrete concluído: se você mandou
     * adiar, o aviso ainda vai reaparecer, e o lembrete não pode sumir antes
     * disso.
     */
    public boolean temAdiamentoPendente(long lembreteId) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT 1 FROM adiamento WHERE lembrete_id = ? AND data_exclusao IS NULL")) {
            ps.setLong(1, lembreteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Log.aviso("Falha ao consultar adiamentos pendentes: " + e.getMessage());
            return true;   // na dúvida, não arquiva
        }
    }

    public void adiar(long lembreteId, LocalDateTime ocorrencia, LocalDateTime alertarEm) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "INSERT INTO adiamento (lembrete_id, ocorrencia, alertar_em) VALUES (?, ?, ?)")) {
            ps.setLong(1, lembreteId);
            ps.setLong(2, paraMillis(ocorrencia));
            ps.setLong(3, paraMillis(alertarEm));
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.erro("Falha ao adiar o lembrete " + lembreteId, e);
        }
    }

    /**
     * Adiamentos cujo horário já chegou.
     *
     * Ao serem devolvidos eles são marcados como consumidos (data_exclusao),
     * e não apagados: assim o alerta não se repete e o registro de que você
     * adiou aquele compromisso permanece.
     */
    public List<Disparo> adiamentosVencidos() {
        List<Disparo> lista = new ArrayList<>();
        long agora = Instant.now().toEpochMilli();
        try (PreparedStatement ps = Database.conexao().prepareStatement("""
                SELECT id, lembrete_id, ocorrencia FROM adiamento
                WHERE alertar_em <= ? AND data_exclusao IS NULL
                """)) {
            ps.setLong(1, agora);
            List<Long> paraConsumir = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    paraConsumir.add(rs.getLong("id"));
                    lista.add(new Disparo(
                            rs.getLong("lembrete_id"),
                            paraData(rs.getLong("ocorrencia")),
                            0,
                            LocalDateTime.now()));
                }
            }
            for (Long id : paraConsumir) {
                try (PreparedStatement marca = Database.conexao()
                        .prepareStatement("UPDATE adiamento SET data_exclusao = ? WHERE id = ?")) {
                    marca.setLong(1, agora);
                    marca.setLong(2, id);
                    marca.executeUpdate();
                }
            }
        } catch (SQLException e) {
            Log.erro("Falha ao consultar adiamentos", e);
        }
        return lista;
    }

    // ------------------------------------------------------------ Proteção

    private String protegerSeNecessario(String texto, boolean sensivel) {
        if (!sensivel || texto == null || texto.isEmpty()) {
            return texto;
        }
        if (!SecurityService.estaDestrancado()) {
            throw new IllegalStateException("Destranque o aplicativo para gravar conteúdo protegido.");
        }
        return CryptoService.cifrarTexto(texto, SecurityService.chave());
    }

    private String revelarSePossivel(String armazenado, boolean sensivel) {
        if (!sensivel || !CryptoService.estaCifrado(armazenado)) {
            return armazenado;
        }
        if (!SecurityService.estaDestrancado()) {
            return PROTEGIDO;
        }
        try {
            return CryptoService.decifrarTexto(armazenado, SecurityService.chave());
        } catch (Exception e) {
            Log.aviso("Não foi possível decifrar um lembrete protegido.");
            return PROTEGIDO;
        }
    }

    // ------------------------------------------------------------ conversao

    private static long paraMillis(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime paraData(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /** Um aviso que foi disparado em determinada ocorrência. */
    public record Disparo(long lembreteId, LocalDateTime ocorrencia, int antecedencia,
                          LocalDateTime disparadoEm) {
    }
}
