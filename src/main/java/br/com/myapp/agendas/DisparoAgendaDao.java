package br.com.myapp.agendas;

import br.com.myapp.core.Log;
import br.com.myapp.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Os avisos de evento de agenda que já saíram: impede o mesmo aviso de sair
 * duas vezes, guarda o "Confirmar" e o "Adiar".
 */
class DisparoAgendaDao {

    /** Um aviso adiado cuja hora de voltar chegou. */
    record Adiado(long id, long agendaId, String uid, LocalDateTime ocorrencia, int antecedencia) {
    }

    /** Chave de um aviso, para conferir em memória sem uma consulta por evento. */
    static String chave(String uid, LocalDateTime ocorrencia, int antecedencia) {
        return uid + "|" + paraMillis(ocorrencia) + "|" + antecedencia;
    }

    /** Avisos desta agenda de ocorrências a partir de um instante, como chaves. */
    Set<String> disparados(long agendaId, LocalDateTime desde) {
        Set<String> chaves = new HashSet<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT uid, ocorrencia, antecedencia FROM disparo_agenda "
                        + "WHERE agenda_id = ? AND ocorrencia >= ?")) {
            ps.setLong(1, agendaId);
            ps.setLong(2, paraMillis(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chaves.add(rs.getString(1) + "|" + rs.getLong(2) + "|" + rs.getInt(3));
                }
            }
        } catch (SQLException e) {
            Log.aviso("Falha ao consultar avisos de agenda: " + e.getMessage());
        }
        return chaves;
    }

    /**
     * Registra que o aviso saiu.
     *
     * @return false se já estava registrado — outra varredura ganhou a corrida
     */
    boolean registrar(long agendaId, String uid, LocalDateTime ocorrencia, int antecedencia) {
        try (PreparedStatement ps = Database.conexao().prepareStatement("""
                INSERT OR IGNORE INTO disparo_agenda
                    (agenda_id, uid, ocorrencia, antecedencia, disparado_em)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, agendaId);
            ps.setString(2, uid);
            ps.setLong(3, paraMillis(ocorrencia));
            ps.setInt(4, antecedencia);
            ps.setLong(5, Instant.now().toEpochMilli());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Log.erro("Falha ao registrar aviso de agenda", e);
            return false;
        }
    }

    /** "Confirmar": todos os avisos desta ocorrência ficam vistos. */
    void reconhecer(long agendaId, String uid, LocalDateTime ocorrencia) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE disparo_agenda SET reconhecido = 1, adiar_ate = NULL "
                        + "WHERE agenda_id = ? AND uid = ? AND ocorrencia = ?")) {
            ps.setLong(1, agendaId);
            ps.setString(2, uid);
            ps.setLong(3, paraMillis(ocorrencia));
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Falha ao confirmar aviso de agenda: " + e.getMessage());
        }
    }

    /** "Adiar": o aviso volta em {@code quando}. */
    void adiar(long agendaId, String uid, LocalDateTime ocorrencia, int antecedencia, LocalDateTime quando) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE disparo_agenda SET adiar_ate = ? "
                        + "WHERE agenda_id = ? AND uid = ? AND ocorrencia = ? AND antecedencia = ?")) {
            ps.setLong(1, paraMillis(quando));
            ps.setLong(2, agendaId);
            ps.setString(3, uid);
            ps.setLong(4, paraMillis(ocorrencia));
            ps.setInt(5, antecedencia);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Falha ao adiar aviso de agenda: " + e.getMessage());
        }
    }

    List<Adiado> adiadosVencidos(LocalDateTime agora) {
        List<Adiado> lista = new ArrayList<>();
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT id, agenda_id, uid, ocorrencia, antecedencia FROM disparo_agenda "
                        + "WHERE adiar_ate IS NOT NULL AND adiar_ate <= ? AND data_exclusao IS NULL")) {
            ps.setLong(1, paraMillis(agora));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Adiado(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            paraData(rs.getLong(4)), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            Log.aviso("Falha ao consultar avisos adiados: " + e.getMessage());
        }
        return lista;
    }

    /** O adiamento já voltou à tela; não volta de novo. */
    void limparAdiamento(long id) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "UPDATE disparo_agenda SET adiar_ate = NULL WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.aviso("Falha ao limpar adiamento: " + e.getMessage());
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
