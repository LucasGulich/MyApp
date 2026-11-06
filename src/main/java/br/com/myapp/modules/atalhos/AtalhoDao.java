package br.com.myapp.modules.atalhos;

import br.com.myapp.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grupos e atalhos no banco.
 *
 * <p>Como no resto do app, nada sai do banco: excluir grava
 * {@code data_exclusao}, e as listagens filtram por ela.
 */
public class AtalhoDao {

    // ============================================================ leitura

    /** Os grupos vivos, em ordem, cada um com os seus atalhos em ordem. */
    public List<GrupoAtalho> grupos() {
        Map<Long, GrupoAtalho> porId = new LinkedHashMap<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT * FROM atalho_grupo WHERE data_exclusao IS NULL ORDER BY ordem, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GrupoAtalho g = new GrupoAtalho();
                g.setId(rs.getLong("id"));
                g.setNome(rs.getString("nome"));
                g.setOrdem(rs.getInt("ordem"));
                porId.put(g.getId(), g);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler os grupos de atalhos", e);
        }

        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT * FROM atalho WHERE data_exclusao IS NULL ORDER BY ordem, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Atalho a = montar(rs);
                GrupoAtalho dono = porId.get(a.getGrupoId());
                // Atalho de grupo excluído fica guardado, fora da vista.
                if (dono != null) {
                    dono.getAtalhos().add(a);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler os atalhos", e);
        }
        return new ArrayList<>(porId.values());
    }

    /** Já existiu algum grupo, mesmo excluído? Decide os exemplos da primeira vez. */
    public boolean jaTeveGrupo() {
        try (PreparedStatement ps = Database.conexao().prepareStatement("SELECT 1 FROM atalho_grupo LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            // Na dúvida, não cria nada: exemplo a mais é pior que nenhum.
            return true;
        }
    }

    private Atalho montar(ResultSet rs) throws SQLException {
        Atalho a = new Atalho();
        a.setId(rs.getLong("id"));
        a.setGrupoId(rs.getLong("grupo_id"));
        a.setNome(rs.getString("nome"));
        a.setTipo(TipoAtalho.doBanco(rs.getString("tipo")));
        a.setAlvo(rs.getString("alvo"));
        a.setArgumentos(rs.getString("argumentos"));
        a.setModoIcone(Atalho.ModoIcone.doBanco(rs.getString("icone_modo")));
        a.setIconePng(rs.getBytes("icone_png"));
        a.setIconeSimbolo(rs.getString("icone_simbolo"));
        a.setCor(rs.getString("cor"));
        a.setNavegador(rs.getString("navegador"));
        a.setOrdem(rs.getInt("ordem"));
        a.setUsos(rs.getInt("usos"));
        return a;
    }

    // ============================================================= grupos

    public GrupoAtalho salvarGrupo(GrupoAtalho g) {
        long agora = Instant.now().toEpochMilli();
        try {
            if (g.getId() == null) {
                try (PreparedStatement ps = Database.conexao().prepareStatement(
                        "INSERT INTO atalho_grupo (nome, ordem, criado_em, atualizado_em) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, g.getNome());
                    ps.setInt(2, proximaOrdem("SELECT MAX(ordem) FROM atalho_grupo WHERE data_exclusao IS NULL", null));
                    ps.setLong(3, agora);
                    ps.setLong(4, agora);
                    ps.executeUpdate();
                    try (ResultSet chaves = ps.getGeneratedKeys()) {
                        if (chaves.next()) {
                            g.setId(chaves.getLong(1));
                        }
                    }
                }
            } else {
                try (PreparedStatement ps = Database.conexao().prepareStatement(
                        "UPDATE atalho_grupo SET nome = ?, atualizado_em = ? WHERE id = ?")) {
                    ps.setString(1, g.getNome());
                    ps.setLong(2, agora);
                    ps.setLong(3, g.getId());
                    ps.executeUpdate();
                }
            }
            return g;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar o grupo", e);
        }
    }

    public void excluirGrupo(long id) {
        marcarExcluido("atalho_grupo", id);
    }

    public void gravarOrdemDosGrupos(List<GrupoAtalho> grupos) {
        gravarOrdem("atalho_grupo", grupos.stream().map(GrupoAtalho::getId).toList());
    }

    // ============================================================ atalhos

    public Atalho salvar(Atalho a) {
        long agora = Instant.now().toEpochMilli();
        try {
            if (a.getId() == null) {
                String sql = """
                        INSERT INTO atalho (grupo_id, nome, tipo, alvo, argumentos, icone_modo, icone_png,
                                            icone_simbolo, cor, navegador, ordem, criado_em, atualizado_em)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = Database.conexao().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    preencher(ps, a);
                    ps.setInt(11, proximaOrdem(
                            "SELECT MAX(ordem) FROM atalho WHERE grupo_id = ? AND data_exclusao IS NULL", a.getGrupoId()));
                    ps.setLong(12, agora);
                    ps.setLong(13, agora);
                    ps.executeUpdate();
                    try (ResultSet chaves = ps.getGeneratedKeys()) {
                        if (chaves.next()) {
                            a.setId(chaves.getLong(1));
                        }
                    }
                }
            } else {
                String sql = """
                        UPDATE atalho SET grupo_id = ?, nome = ?, tipo = ?, alvo = ?, argumentos = ?,
                               icone_modo = ?, icone_png = ?, icone_simbolo = ?, cor = ?, navegador = ?, ordem = ?,
                               atualizado_em = ?
                        WHERE id = ?
                        """;
                try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                    preencher(ps, a);
                    ps.setInt(11, a.getOrdem());
                    ps.setLong(12, agora);
                    ps.setLong(13, a.getId());
                    ps.executeUpdate();
                }
            }
            return a;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar o atalho", e);
        }
    }

    private void preencher(PreparedStatement ps, Atalho a) throws SQLException {
        ps.setLong(1, a.getGrupoId());
        ps.setString(2, a.getNome());
        ps.setString(3, a.getTipo().name());
        ps.setString(4, a.getAlvo());
        ps.setString(5, a.getArgumentos());
        ps.setString(6, a.getModoIcone().name());
        ps.setBytes(7, a.getIconePng());
        ps.setString(8, a.getIconeSimbolo());
        ps.setString(9, a.getCor());
        ps.setString(10, a.getNavegador().isBlank() ? null : a.getNavegador());
    }

    public void excluir(long id) {
        marcarExcluido("atalho", id);
    }

    public void registrarUso(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE atalho SET usos = usos + 1, ultimo_uso = ? WHERE id = ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar o uso do atalho", e);
        }
    }

    public void gravarOrdemDosAtalhos(List<Atalho> atalhos) {
        gravarOrdem("atalho", atalhos.stream().map(Atalho::getId).toList());
    }

    // ============================================================== apoio

    private void marcarExcluido(String tabela, long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE " + tabela + " SET data_exclusao = ? WHERE id = ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir de " + tabela, e);
        }
    }

    private void gravarOrdem(String tabela, List<Long> ids) {
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE " + tabela + " SET ordem = ? WHERE id = ?")) {
                int ordem = 0;
                for (Long id : ids) {
                    ps.setInt(1, ordem++);
                    ps.setLong(2, id);
                    ps.addBatch();
                }
                ps.executeBatch();
                Database.conexao().commit();
            } catch (SQLException e) {
                Database.conexao().rollback();
                throw e;
            } finally {
                Database.conexao().setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar a ordem em " + tabela, e);
        }
    }

    private int proximaOrdem(String sql, Long parametro) throws SQLException {
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            if (parametro != null) {
                ps.setLong(1, parametro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getObject(1) != null ? rs.getInt(1) + 1 : 0;
            }
        }
    }
}
