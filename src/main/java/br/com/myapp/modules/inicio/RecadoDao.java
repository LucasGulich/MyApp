package br.com.myapp.modules.inicio;

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

/** Acesso ao banco para os recados da tela inicial. */
public class RecadoDao {

    // ------------------------------------------------------------- escrita

    public Recado salvar(Recado recado) {
        return recado.getId() == null ? inserir(recado) : atualizar(recado);
    }

    private Recado inserir(Recado r) {
        String sql = """
                INSERT INTO recado (texto, cor, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            r.setAtualizadoEm(LocalDateTime.now());
            ps.setString(1, r.getTexto());
            ps.setString(2, r.getCor().name());
            ps.setInt(3, r.getOrdem());
            ps.setLong(4, paraMillis(r.getCriadoEm()));
            ps.setLong(5, paraMillis(r.getAtualizadoEm()));
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    r.setId(chaves.getLong(1));
                }
            }
            Log.info("Recado criado: id=" + r.getId());
            return r;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o recado", e);
        }
    }

    private Recado atualizar(Recado r) {
        String sql = """
                UPDATE recado SET texto = ?, cor = ?, ordem = ?, atualizado_em = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
            r.setAtualizadoEm(LocalDateTime.now());
            ps.setString(1, r.getTexto());
            ps.setString(2, r.getCor().name());
            ps.setInt(3, r.getOrdem());
            ps.setLong(4, paraMillis(r.getAtualizadoEm()));
            ps.setLong(5, r.getId());
            ps.executeUpdate();
            return r;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao alterar o recado", e);
        }
    }

    /**
     * Grava a ordem de todos de uma vez.
     *
     * Chamado depois de arrastar: reposicionar um recado muda a posição dos
     * demais, e gravar um por vez deixaria o arranjo inconsistente se algo
     * falhasse no meio.
     */
    public void gravarOrdem(List<Recado> recados) {
        String sql = "UPDATE recado SET ordem = ?, atualizado_em = ? WHERE id = ?";
        try {
            Database.conexao().setAutoCommit(false);
            try (PreparedStatement ps = Database.conexao().prepareStatement(sql)) {
                long agora = Instant.now().toEpochMilli();
                int ordem = 0;
                for (Recado r : recados) {
                    r.setOrdem(ordem);
                    ps.setInt(1, ordem++);
                    ps.setLong(2, agora);
                    ps.setLong(3, r.getId());
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
            throw new IllegalStateException("Falha ao gravar a ordem dos recados", e);
        } finally {
            try {
                Database.conexao().setAutoCommit(true);
            } catch (SQLException ignorado) {
                // Idem.
            }
        }
    }

    /** Exclusão lógica: some da tela, continua no banco. */
    public void excluir(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE recado SET data_exclusao = ?, atualizado_em = ? WHERE id = ?")) {
            long agora = Instant.now().toEpochMilli();
            ps.setLong(1, agora);
            ps.setLong(2, agora);
            ps.setLong(3, id);
            ps.executeUpdate();
            Log.info("Recado excluido (logicamente): id=" + id);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao excluir o recado", e);
        }
    }

    // -------------------------------------------------------------- leitura

    public List<Recado> listar() {
        List<Recado> lista = new ArrayList<>();
        String sql = "SELECT * FROM recado WHERE data_exclusao IS NULL ORDER BY ordem, id";
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar os recados", e);
        }
        return lista;
    }

    public Optional<Recado> porId(long id) {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT * FROM recado WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar o recado", e);
        }
    }

    /** Próxima posição livre, para o recado novo nascer no fim. */
    public int proximaOrdem() {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT COALESCE(MAX(ordem), -1) + 1 FROM recado WHERE data_exclusao IS NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private Recado montar(ResultSet rs) throws SQLException {
        Recado r = new Recado();
        r.setId(rs.getLong("id"));
        r.setTexto(rs.getString("texto"));
        r.setCor(CorRecado.de(rs.getString("cor")));
        r.setOrdem(rs.getInt("ordem"));
        r.setCriadoEm(paraData(rs.getLong("criado_em")));
        r.setAtualizadoEm(paraData(rs.getLong("atualizado_em")));

        long exclusao = rs.getLong("data_exclusao");
        r.setDataExclusao(rs.wasNull() ? null : paraData(exclusao));
        return r;
    }

    // ------------------------------------------------------------ conversão

    private static long paraMillis(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime paraData(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
