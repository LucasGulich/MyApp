package br.com.myapp.modules.kanban;

import br.com.myapp.core.Log;
import br.com.myapp.data.Database;
import br.com.myapp.security.CryptoService;
import br.com.myapp.security.SecurityService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acesso ao banco para o quadro Kanban.
 *
 * <p>Colunas e cartões moram no mesmo DAO porque nunca são lidos separados: a
 * tela pede o quadro, e o quadro é as duas coisas. Duas classes obrigariam a
 * costurar os dois resultados em algum lugar, e esse lugar seria aqui de
 * qualquer forma.
 *
 * <p>Como em todo o aplicativo, <b>nada é apagado</b>: excluir grava uma data
 * em {@code data_exclusao} e as consultas passam a ignorar a linha.
 */
public class KanbanDao {

    /** O que aparece no lugar do texto de um cartão protegido, com o app trancado. */
    public static final String PROTEGIDO = "Cartão protegido";

    // ============================================================== leitura

    /**
     * O quadro inteiro: colunas na ordem, cada uma com os seus cartões.
     *
     * <p>Duas consultas, e não uma com junção: a junção traria os dados da
     * coluna repetidos em cada cartão, e ainda deixaria de fora as colunas
     * vazias — que num Kanban são justamente as que mais importam, porque é
     * nelas que se vai soltar alguma coisa.
     */
    public List<ColunaKanban> quadro() {
        Map<Long, ColunaKanban> porId = new LinkedHashMap<>();

        String sqlColunas = """
                SELECT * FROM kanban_coluna
                WHERE data_exclusao IS NULL
                ORDER BY ordem, id
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sqlColunas);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ColunaKanban c = montarColuna(rs);
                porId.put(c.getId(), c);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler as colunas do quadro", e);
        }

        if (porId.isEmpty()) {
            return new ArrayList<>();
        }

        String sqlCards = """
                SELECT * FROM kanban_card
                WHERE data_exclusao IS NULL
                ORDER BY ordem, id
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sqlCards);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CardKanban card = montarCard(rs);
                ColunaKanban dona = porId.get(card.getColunaId());
                // Cartão cuja coluna foi excluída não tem onde aparecer; fica
                // no banco, fora da vista, até a coluna ser restaurada.
                if (dona != null) {
                    dona.getCards().add(card);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler os cartões do quadro", e);
        }

        return new ArrayList<>(porId.values());
    }

    public Optional<ColunaKanban> colunaPorId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM kanban_coluna WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montarColuna(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler a coluna " + id, e);
        }
    }

    // ======================================================= escrita: coluna

    public ColunaKanban salvarColuna(ColunaKanban c) {
        return c.getId() == null ? inserirColuna(c) : atualizarColuna(c);
    }

    private ColunaKanban inserirColuna(ColunaKanban c) {
        String sql = """
                INSERT INTO kanban_coluna (nome, cor, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?)
                """;
        long agora = Instant.now().toEpochMilli();
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNome());
            ps.setString(2, c.getCor().paraBanco());
            ps.setInt(3, c.getOrdem() > 0 ? c.getOrdem() : proximaOrdemDeColuna());
            ps.setLong(4, agora);
            ps.setLong(5, agora);
            ps.executeUpdate();

            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    c.setId(chaves.getLong(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar a coluna", e);
        }
    }

    private ColunaKanban atualizarColuna(ColunaKanban c) {
        String sql = """
                UPDATE kanban_coluna
                SET nome = ?, cor = ?, ordem = ?, atualizado_em = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setString(1, c.getNome());
            ps.setString(2, c.getCor().paraBanco());
            ps.setInt(3, c.getOrdem());
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.setLong(5, c.getId());
            ps.executeUpdate();
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar a coluna " + c.getId(), e);
        }
    }

    /**
     * Exclui a coluna e, junto, os cartões dela.
     *
     * <p>Aqui a cascata é intencional, ao contrário das categorias de notas:
     * um cartão sem coluna não tem onde aparecer num quadro, enquanto uma
     * nota sem categoria continua perfeitamente acessível. E como é exclusão
     * lógica, restaurar a coluna traz os cartões de volta com ela.
     */
    public void excluirColuna(long id) {
        long agora = Instant.now().toEpochMilli();
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE kanban_coluna SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
                ps.setLong(1, agora);
                ps.setLong(2, agora);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE kanban_card SET data_exclusao = ?, atualizado_em = ? "
                            + "WHERE coluna_id = ? AND data_exclusao IS NULL")) {
                ps.setLong(1, agora);
                ps.setLong(2, agora);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            Database.conexao().commit();
        } catch (SQLException e) {
            desfazer();
            throw new IllegalStateException("Falha ao excluir a coluna " + id, e);
        } finally {
            religarAutoCommit();
        }
    }

    private int proximaOrdemDeColuna() {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT COALESCE(MAX(ordem), -1) + 1 FROM kanban_coluna WHERE data_exclusao IS NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    // ======================================================== escrita: card

    public CardKanban salvarCard(CardKanban c) {
        return c.getId() == null ? inserirCard(c) : atualizarCard(c);
    }

    private CardKanban inserirCard(CardKanban c) {
        String sql = """
                INSERT INTO kanban_card
                    (coluna_id, titulo, descricao, prazo, cor, ordem,
                     arquivado, protegido, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        long agora = Instant.now().toEpochMilli();
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, c.getColunaId());
            ps.setString(2, protegerSeNecessario(c.getTitulo(), c.isProtegido()));
            ps.setString(3, protegerSeNecessario(c.getDescricao(), c.isProtegido()));
            ps.setString(4, c.getPrazo() == null ? null : c.getPrazo().toString());
            ps.setString(5, c.getCor().paraBanco());
            ps.setInt(6, proximaOrdemNaColuna(c.getColunaId()));
            ps.setInt(7, c.isArquivado() ? 1 : 0);
            ps.setInt(8, c.isProtegido() ? 1 : 0);
            ps.setLong(9, agora);
            ps.setLong(10, agora);
            ps.executeUpdate();

            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    c.setId(chaves.getLong(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o cartão", e);
        }
    }

    private CardKanban atualizarCard(CardKanban c) {
        String sql = """
                UPDATE kanban_card
                SET coluna_id = ?, titulo = ?, descricao = ?, prazo = ?, cor = ?,
                    ordem = ?, arquivado = ?, protegido = ?, atualizado_em = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setLong(1, c.getColunaId());
            ps.setString(2, protegerSeNecessario(c.getTitulo(), c.isProtegido()));
            ps.setString(3, protegerSeNecessario(c.getDescricao(), c.isProtegido()));
            ps.setString(4, c.getPrazo() == null ? null : c.getPrazo().toString());
            ps.setString(5, c.getCor().paraBanco());
            ps.setInt(6, c.getOrdem());
            ps.setInt(7, c.isArquivado() ? 1 : 0);
            ps.setInt(8, c.isProtegido() ? 1 : 0);
            ps.setLong(9, Instant.now().toEpochMilli());
            ps.setLong(10, c.getId());
            ps.executeUpdate();
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar o cartão " + c.getId(), e);
        }
    }

    public void excluirCard(long id) {
        long agora = Instant.now().toEpochMilli();
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE kanban_card SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir o cartão " + id, e);
        }
    }

    /**
     * Liga e desliga o arquivamento.
     *
     * <p>É a única alteração que não passa por {@code salvarCard}: mexer só
     * na coluna {@code arquivado} evita reescrever título e descrição — o que,
     * num cartão protegido com o aplicativo trancado, seria impossível.
     */
    public void definirArquivado(long id, boolean arquivado) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE kanban_card SET arquivado = ?, atualizado_em = ? WHERE id = ?")) {
            ps.setInt(1, arquivado ? 1 : 0);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao arquivar o cartão " + id, e);
        }
    }

    private int proximaOrdemNaColuna(long colunaId) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT COALESCE(MAX(ordem), -1) + 1 FROM kanban_card "
                        + "WHERE coluna_id = ? AND data_exclusao IS NULL")) {
            ps.setLong(1, colunaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ============================================================ reordenar

    /** Grava a ordem das colunas, em uma transação só. */
    public void gravarOrdemDasColunas(List<ColunaKanban> colunas) {
        String sql = "UPDATE kanban_coluna SET ordem = ?, atualizado_em = ? WHERE id = ?";
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (ColunaKanban c : colunas) {
                    c.setOrdem(ordem);
                    ps.setInt(1, ordem++);
                    ps.setLong(2, agora);
                    ps.setLong(3, c.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            Database.conexao().commit();
        } catch (SQLException e) {
            desfazer();
            throw new IllegalStateException("Falha ao gravar a ordem das colunas", e);
        } finally {
            religarAutoCommit();
        }
    }

    /**
     * Grava a coluna e a posição de cada cartão da lista.
     *
     * <p>Serve tanto para reordenar dentro de uma coluna quanto para o cartão
     * que acabou de mudar de coluna: nos dois casos o que se grava é "estes
     * cartões, nesta ordem, pertencem a esta coluna".
     */
    public void gravarOrdemDosCards(long colunaId, List<CardKanban> cards) {
        String sql = "UPDATE kanban_card SET coluna_id = ?, ordem = ?, atualizado_em = ? WHERE id = ?";
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (CardKanban c : cards) {
                    c.setColunaId(colunaId);
                    c.setOrdem(ordem);
                    ps.setLong(1, colunaId);
                    ps.setInt(2, ordem++);
                    ps.setLong(3, agora);
                    ps.setLong(4, c.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            Database.conexao().commit();
        } catch (SQLException e) {
            desfazer();
            throw new IllegalStateException("Falha ao gravar a ordem dos cartões", e);
        } finally {
            religarAutoCommit();
        }
    }

    // ============================================================== montagem

    private ColunaKanban montarColuna(ResultSet rs) throws SQLException {
        ColunaKanban c = new ColunaKanban();
        c.setId(rs.getLong("id"));
        c.setNome(rs.getString("nome"));
        c.setCor(CorKanban.de(rs.getString("cor")));
        c.setOrdem(rs.getInt("ordem"));
        c.setCriadoEm(paraData(rs.getLong("criado_em")));
        c.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long exclusao = rs.getLong("data_exclusao");
        c.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        return c;
    }

    private CardKanban montarCard(ResultSet rs) throws SQLException {
        CardKanban c = new CardKanban();
        c.setId(rs.getLong("id"));
        c.setColunaId(rs.getLong("coluna_id"));
        c.setProtegido(rs.getInt("protegido") == 1);
        c.setTitulo(revelarSePossivel(rs.getString("titulo"), c.isProtegido()));
        c.setDescricao(revelarSePossivel(rs.getString("descricao"), c.isProtegido()));

        String prazo = rs.getString("prazo");
        c.setPrazo(prazo == null || prazo.isBlank() ? null : LocalDate.parse(prazo));

        c.setCor(CorKanban.de(rs.getString("cor")));
        c.setOrdem(rs.getInt("ordem"));
        c.setArquivado(rs.getInt("arquivado") == 1);
        c.setCriadoEm(paraData(rs.getLong("criado_em")));
        c.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long exclusao = rs.getLong("data_exclusao");
        c.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        return c;
    }

    // ============================================================= proteção

    private String protegerSeNecessario(String texto, boolean protegido) {
        if (!protegido || texto == null || texto.isEmpty()) {
            return texto;
        }
        if (!SecurityService.estaDestrancado()) {
            throw new IllegalStateException(
                    "Destranque o aplicativo para gravar conteúdo protegido.");
        }
        return CryptoService.cifrarTexto(texto, SecurityService.chave());
    }

    private String revelarSePossivel(String armazenado, boolean protegido) {
        if (!protegido || !CryptoService.estaCifrado(armazenado)) {
            return armazenado;
        }
        if (!SecurityService.estaDestrancado()) {
            return PROTEGIDO;
        }
        try {
            return CryptoService.decifrarTexto(armazenado, SecurityService.chave());
        } catch (Exception e) {
            Log.aviso("Não foi possível decifrar um cartão protegido.");
            return PROTEGIDO;
        }
    }

    // ============================================================== apoio

    private void desfazer() {
        try {
            Database.conexao().rollback();
        } catch (SQLException ignorado) {
            // Nada a fazer: o erro original é o que importa.
        }
    }

    private void religarAutoCommit() {
        try {
            Database.conexao().setAutoCommit(true);
        } catch (SQLException ignorado) {
            // Idem.
        }
    }

    private static LocalDateTime paraData(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
