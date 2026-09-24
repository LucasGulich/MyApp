package br.com.myapp.modules.notas;

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
 * Acesso ao banco para as notas.
 *
 * Duas regras de cifragem convivem aqui:
 *
 *  - <b>nota protegida</b>: título, corpo e todos os campos vão cifrados;
 *  - <b>campo de segredo</b> (a senha de uma credencial): vai cifrado sempre,
 *    mesmo que a nota não esteja marcada como protegida.
 *
 * A segunda regra é deliberada. Um valor cuja chave é "Senha" não tem por que
 * existir em texto claro no arquivo do banco, em nenhuma circunstância.
 */
public class NotaDao {

    /** Exibido no lugar do conteúdo quando o aplicativo está trancado. */
    public static final String PROTEGIDO = "Nota protegida";

    /** Exibido no lugar de um segredo que não pode ser lido agora. */
    public static final String SEGREDO_OCULTO = "••••••";

    // ------------------------------------------------------------- escrita

    public Nota salvar(Nota nota) {
        boolean novo = nota.getId() == null;
        Nota salva = novo ? inserir(nota) : atualizar(nota);
        gravarCampos(salva);
        return salva;
    }

    private Nota inserir(Nota n) {
        String sql = """
                INSERT INTO nota (categoria_id, tipo, titulo, conteudo, protegida,
                                  favorita, fixada, criado_em, atualizado_em, data_exclusao, ordem)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            n.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, n);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    n.setId(chaves.getLong(1));
                }
            }
            Log.info("Nota criada: id=" + n.getId() + " tipo=" + n.getTipo());
            return n;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar a nota", e);
        }
    }

    private Nota atualizar(Nota n) {
        String sql = """
                UPDATE nota SET categoria_id = ?, tipo = ?, titulo = ?, conteudo = ?,
                                protegida = ?, favorita = ?, fixada = ?, criado_em = ?,
                                atualizado_em = ?, data_exclusao = ?, ordem = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            n.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, n);
            ps.setLong(12, n.getId());
            ps.executeUpdate();
            Log.info("Nota alterada: id=" + n.getId());
            return n;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao alterar a nota", e);
        }
    }

    private void preencher(PreparedStatement ps, Nota n) throws SQLException {
        if (n.getCategoriaId() == null) {
            ps.setNull(1, java.sql.Types.INTEGER);
        } else {
            ps.setLong(1, n.getCategoriaId());
        }
        ps.setString(2, n.getTipo().name());
        ps.setString(3, proteger(n.getTitulo(), n.isProtegida()));
        ps.setString(4, proteger(n.getCorpo(), n.isProtegida()));
        ps.setInt(5, n.isProtegida() ? 1 : 0);
        ps.setInt(6, n.isFavorita() ? 1 : 0);
        ps.setInt(7, n.isFixada() ? 1 : 0);
        ps.setLong(8, paraMillis(n.getCriadoEm()));
        ps.setLong(9, paraMillis(n.getAtualizadoEm()));
        if (n.getDataExclusao() == null) {
            ps.setNull(10, java.sql.Types.INTEGER);
        } else {
            ps.setLong(10, paraMillis(n.getDataExclusao()));
        }
        ps.setInt(11, n.getOrdem());
    }

    /**
     * Regrava os campos da nota.
     *
     * Antes de sobrescrever um segredo, guarda o valor anterior em
     * nota_historico — é o que permite recuperar a senha antiga quando a nova
     * não é aceita pelo sistema do cliente.
     */
    private void gravarCampos(Nota n) {
        try {
            List<CampoNota> anteriores = carregarCampos(n.getId(), n.isProtegida());

            for (CampoNota campo : n.getCampos()) {
                if (campo.isSensivel()) {
                    anteriores.stream()
                            .filter(a -> a.getChave().equalsIgnoreCase(campo.getChave()))
                            .filter(a -> !a.estaVazio())
                            .filter(a -> !a.getValor().equals(campo.getValor()))
                            .findFirst()
                            .ifPresent(a -> registrarNoHistorico(n, a));
                }
            }

            // Marca os campos atuais como substituídos e grava a versão nova.
            // Nada é apagado: a linha antiga continua, com data_exclusao.
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE nota_campo SET data_exclusao = ? WHERE nota_id = ? AND data_exclusao IS NULL")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setLong(2, n.getId());
                ps.executeUpdate();
            }

            String sql = """
                    INSERT INTO nota_campo (nota_id, chave, valor, sensivel, ordem)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                int ordem = 0;
                for (CampoNota campo : n.getCampos()) {
                    ps.setLong(1, n.getId());
                    ps.setString(2, campo.getChave());
                    // Segredo é cifrado sempre; campo comum, só se a nota for protegida.
                    ps.setString(3, proteger(campo.getValor(), n.isProtegida() || campo.isSensivel()));
                    ps.setInt(4, campo.isSensivel() ? 1 : 0);
                    ps.setInt(5, ordem++);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar os campos da nota", e);
        }
    }

    private void registrarNoHistorico(Nota n, CampoNota anterior) {
        try (PreparedStatement ps = Database.conexao().prepareStatement("""
                INSERT INTO nota_historico (nota_id, chave, valor_anterior, trocado_em)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setLong(1, n.getId());
            ps.setString(2, anterior.getChave());
            ps.setString(3, proteger(anterior.getValor(), true));   // histórico sempre cifrado
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Não foi possível guardar o valor anterior: " + e.getMessage());
        }
    }

    /** Exclusão lógica. */
    public void excluir(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE nota SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            long agora = Instant.now().toEpochMilli();
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
            Log.info("Nota excluida (logicamente): id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir a nota", e);
        }
    }

    public void restaurar(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE nota SET data_exclusao = NULL, atualizado_em = ? WHERE id = ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, id);
            ps.executeUpdate();
            Log.info("Nota restaurada: id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao restaurar a nota", e);
        }
    }

    /** Grava a ordem manual de todas de uma vez. */
    public void gravarOrdem(List<Nota> naNovaOrdem) {
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE nota SET ordem = ?, atualizado_em = ? WHERE id = ?")) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (Nota n : naNovaOrdem) {
                    n.setOrdem(ordem);
                    ps.setInt(1, ordem++);
                    ps.setLong(2, agora);
                    ps.setLong(3, n.getId());
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
            throw new IllegalStateException("Falha ao gravar a ordem das notas", e);
        } finally {
            try {
                Database.conexao().setAutoCommit(true);
            } catch (SQLException ignorado) {
                // Idem.
            }
        }
    }

    /** Alguma nota ja foi arrastada? So entao a ordem manual vale. */
    public boolean temOrdemManual() {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT 1 FROM nota WHERE data_exclusao IS NULL AND ordem >= 0 LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void definirFavorita(long id, boolean favorita) {
        alternar(id, "favorita", favorita);
    }

    public void definirFixada(long id, boolean fixada) {
        alternar(id, "fixada", fixada);
    }

    private void alternar(long id, String coluna, boolean valor) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE nota SET " + coluna + " = ?, atualizado_em = ? WHERE id = ?")) {
            ps.setInt(1, valor ? 1 : 0);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao atualizar a nota", e);
        }
    }

    // -------------------------------------------------------------- leitura

    /** Notas ativas, com os fixados no topo. */
    public List<Nota> listar() {
        return consultar("""
                SELECT * FROM nota
                WHERE data_exclusao IS NULL
                ORDER BY fixada DESC, favorita DESC, atualizado_em DESC
                """);
    }

    public List<Nota> listarPorCategoria(long categoriaId) {
        try (PreparedStatement ps = Database.conexao().prepareStatement("""
                SELECT * FROM nota
                WHERE data_exclusao IS NULL AND categoria_id = ?
                ORDER BY fixada DESC, favorita DESC, atualizado_em DESC
                """)) {
            ps.setLong(1, categoriaId);
            return montarLista(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar as notas da categoria", e);
        }
    }

    /** Notas sem categoria, para elas não sumirem da interface. */
    public List<Nota> listarSemCategoria() {
        return consultar("""
                SELECT * FROM nota
                WHERE data_exclusao IS NULL AND categoria_id IS NULL
                ORDER BY fixada DESC, favorita DESC, atualizado_em DESC
                """);
    }

    public List<Nota> listarFavoritas() {
        return consultar("""
                SELECT * FROM nota
                WHERE data_exclusao IS NULL AND favorita = 1
                ORDER BY atualizado_em DESC
                """);
    }

    public List<Nota> listarExcluidas() {
        return consultar("""
                SELECT * FROM nota
                WHERE data_exclusao IS NOT NULL
                ORDER BY data_exclusao DESC
                """);
    }

    /** Busca, incluindo os excluídos quando pedido. */
    public Optional<Nota> porId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM nota WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a nota", e);
        }
    }

    private List<Nota> consultar(String sql) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            return montarLista(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar as notas", e);
        }
    }

    private List<Nota> montarLista(PreparedStatement ps) throws SQLException {
        List<Nota> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        }
        return lista;
    }

    private Nota montar(ResultSet rs) throws SQLException {
        Nota n = new Nota();
        n.setId(rs.getLong("id"));
        n.setProtegida(rs.getInt("protegida") == 1);
        n.setTipo(TipoNota.valueOf(rs.getString("tipo")));
        n.setTitulo(revelar(rs.getString("titulo"), n.isProtegida(), PROTEGIDO));
        n.setCorpo(revelar(rs.getString("conteudo"), n.isProtegida(), ""));

        long categoria = rs.getLong("categoria_id");
        n.setCategoriaId(rs.wasNull() ? null : categoria);

        n.setFavorita(rs.getInt("favorita") == 1);
        n.setFixada(rs.getInt("fixada") == 1);
        n.setCriadoEm(paraData(rs.getLong("criado_em")));
        n.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long exclusao = rs.getLong("data_exclusao");
        n.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        n.setOrdem(rs.getInt("ordem"));

        n.getCampos().addAll(carregarCampos(n.getId(), n.isProtegida()));
        return n;
    }

    private List<CampoNota> carregarCampos(Long notaId, boolean notaProtegida) {
        List<CampoNota> lista = new ArrayList<>();
        if (notaId == null) {
            return lista;
        }
        String sql = """
                SELECT * FROM nota_campo
                WHERE nota_id = ? AND data_exclusao IS NULL
                ORDER BY ordem
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setLong(1, notaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CampoNota campo = new CampoNota();
                    campo.setId(rs.getLong("id"));
                    campo.setChave(rs.getString("chave"));
                    campo.setSensivel(rs.getInt("sensivel") == 1);
                    campo.setOrdem(rs.getInt("ordem"));
                    campo.setValor(revelar(rs.getString("valor"),
                            notaProtegida || campo.isSensivel(), SEGREDO_OCULTO));
                    lista.add(campo);
                }
            }
        } catch (SQLException e) {
            Log.erro("Falha ao carregar os campos da nota " + notaId, e);
        }
        return lista;
    }

    /** Valores anteriores de um segredo, do mais recente para o mais antigo. */
    public List<ValorAnterior> historico(long notaId, String chave) {
        List<ValorAnterior> lista = new ArrayList<>();
        String sql = """
                SELECT valor_anterior, trocado_em FROM nota_historico
                WHERE nota_id = ? AND LOWER(chave) = LOWER(?)
                ORDER BY trocado_em DESC
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setLong(1, notaId);
            ps.setString(2, chave);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ValorAnterior(
                            revelar(rs.getString("valor_anterior"), true, SEGREDO_OCULTO),
                            paraData(rs.getLong("trocado_em"))));
                }
            }
        } catch (SQLException e) {
            Log.aviso("Falha ao ler o histórico: " + e.getMessage());
        }
        return lista;
    }

    /** Um valor que foi substituído, e quando. */
    public record ValorAnterior(String valor, LocalDateTime trocadoEm) {
    }

    // ------------------------------------------------------------ proteção

    private String proteger(String texto, boolean cifrar) {
        if (!cifrar || texto == null || texto.isEmpty()) {
            return texto;
        }
        if (!SecurityService.estaDestrancado()) {
            throw new IllegalStateException("Destranque o aplicativo para gravar este conteúdo.");
        }
        return CryptoService.cifrarTexto(texto, SecurityService.chave());
    }

    private String revelar(String armazenado, boolean cifrado, String textoQuandoTrancado) {
        if (!cifrado || !CryptoService.estaCifrado(armazenado)) {
            return armazenado;
        }
        if (!SecurityService.estaDestrancado()) {
            return textoQuandoTrancado;
        }
        try {
            return CryptoService.decifrarTexto(armazenado, SecurityService.chave());
        } catch (Exception e) {
            Log.aviso("Não foi possível decifrar um conteúdo protegido.");
            return textoQuandoTrancado;
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
