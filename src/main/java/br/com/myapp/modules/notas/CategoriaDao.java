package br.com.myapp.modules.notas;

import br.com.myapp.core.Log;
import br.com.myapp.data.Database;

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

/** Acesso ao banco para as categorias de notas. */
public class CategoriaDao {

    // ------------------------------------------------------------- escrita

    public Categoria salvar(Categoria c) {
        return c.getId() == null ? inserir(c) : atualizar(c);
    }

    private Categoria inserir(Categoria c) {
        String sql = """
                INSERT INTO categoria (nome, cor, icone, categoria_pai_id, ordem,
                                       exige_desbloqueio, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, c);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    c.setId(chaves.getLong(1));
                }
            }
            Log.info("Categoria criada: id=" + c.getId());
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar a categoria", e);
        }
    }

    private Categoria atualizar(Categoria c) {
        String sql = """
                UPDATE categoria SET nome = ?, cor = ?, icone = ?, categoria_pai_id = ?,
                                     ordem = ?, exige_desbloqueio = ?, criado_em = ?,
                                     atualizado_em = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            c.setAtualizadoEm(LocalDateTime.now());
            preencher(ps, c);
            ps.setLong(9, c.getId());
            ps.executeUpdate();
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao alterar a categoria", e);
        }
    }

    private void preencher(PreparedStatement ps, Categoria c) throws SQLException {
        ps.setString(1, c.getNome());
        ps.setString(2, c.getCor());
        ps.setString(3, c.getIcone());
        if (c.getCategoriaPaiId() == null) {
            ps.setNull(4, java.sql.Types.INTEGER);
        } else {
            ps.setLong(4, c.getCategoriaPaiId());
        }
        ps.setInt(5, c.getOrdem());
        ps.setInt(6, c.isExigeDesbloqueio() ? 1 : 0);
        ps.setLong(7, paraMillis(c.getCriadoEm()));
        ps.setLong(8, paraMillis(c.getAtualizadoEm()));
    }

    /** Exclusão lógica: a categoria some da lista, sem sair do banco. */
    public void excluir(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE categoria SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            long agora = Instant.now().toEpochMilli();
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
            Log.info("Categoria excluida (logicamente): id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir a categoria", e);
        }
    }

    public void restaurar(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE categoria SET data_exclusao = NULL, atualizado_em = ? WHERE id = ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao restaurar a categoria", e);
        }
    }

    // -------------------------------------------------------------- leitura

    /** Categorias ativas, já com a contagem de notas de cada uma. */
    public List<Categoria> listar() {
        String sql = """
                SELECT c.*, (SELECT COUNT(*) FROM nota n
                             WHERE n.categoria_id = c.id AND n.data_exclusao IS NULL) AS total
                FROM categoria c
                WHERE c.data_exclusao IS NULL
                ORDER BY c.ordem, c.nome
                """;
        List<Categoria> lista = new ArrayList<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Categoria c = montar(rs);
                c.setQuantidadeDeNotas(rs.getInt("total"));
                lista.add(c);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar as categorias", e);
        }
        return lista;
    }

    public Optional<Categoria> porId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM categoria WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a categoria", e);
        }
    }

    /** Já existe categoria com este nome? Usado para evitar duplicidade. */
    public boolean existeComNome(String nome, Long ignorarId) {
        String sql = """
                SELECT 1 FROM categoria
                WHERE data_exclusao IS NULL AND LOWER(nome) = LOWER(?) AND id <> ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            ps.setString(1, nome == null ? "" : nome.trim());
            ps.setLong(2, ignorarId == null ? -1 : ignorarId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Grava a ordem escolhida ao arrastar.
     *
     * Em uma transação só: metade da lista reordenada é pior do que nenhuma,
     * porque duas categorias passariam a disputar a mesma posição e a lista
     * ficaria com um arranjo que você não escolheu.
     */
    public void gravarOrdem(List<Categoria> categorias) {
        String sql = "UPDATE categoria SET ordem = ?, atualizado_em = ? WHERE id = ?";
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (Categoria c : categorias) {
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
            try {
                Database.conexao().rollback();
            } catch (SQLException ignorado) {
                // Nada a fazer: o erro original é o que importa.
            }
            throw new IllegalStateException("Falha ao gravar a ordem das categorias", e);
        } finally {
            try {
                Database.conexao().setAutoCommit(true);
            } catch (SQLException ignorado) {
                // Idem.
            }
        }
    }

    /** Maior ordem em uso, para posicionar uma categoria nova no fim. */
    public int proximaOrdem() {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT COALESCE(MAX(ordem), -1) + 1 FROM categoria");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private Categoria montar(ResultSet rs) throws SQLException {
        Categoria c = new Categoria();
        c.setId(rs.getLong("id"));
        c.setNome(rs.getString("nome"));
        c.setCor(rs.getString("cor"));
        c.setIcone(rs.getString("icone"));

        long pai = rs.getLong("categoria_pai_id");
        c.setCategoriaPaiId(rs.wasNull() ? null : pai);

        c.setOrdem(rs.getInt("ordem"));
        c.setExigeDesbloqueio(rs.getInt("exige_desbloqueio") == 1);
        c.setCriadoEm(paraData(rs.getLong("criado_em")));
        c.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long exclusao = rs.getLong("data_exclusao");
        c.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        return c;
    }

    // ------------------------------------------------------------ conversão

    private static long paraMillis(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime paraData(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
