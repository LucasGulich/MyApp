package br.com.myapp.modules.inicio;

import br.com.myapp.core.EventBus;
import br.com.myapp.modules.lembretes.CalculadoraOcorrencias;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteDao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Regras da tela inicial.
 *
 * Duas responsabilidades: montar a agenda do dia a partir dos lembretes, e
 * cuidar dos recados.
 *
 * A agenda é só leitura — a tela inicial não altera lembrete nenhum. Ela
 * responde "o que tenho para hoje?" usando exatamente o mesmo cálculo de
 * ocorrências que o agendador usa para disparar os avisos, de modo que o que
 * aparece aqui é o que vai realmente tocar.
 */
public class InicioService {

    /** Publicado quando um recado é criado, alterado, movido ou excluído. */
    public record RecadosMudaram() {
    }

    private final RecadoDao recadoDao = new RecadoDao();
    private final LembreteDao lembreteDao = new LembreteDao();

    // ------------------------------------------------------- agenda do dia

    /** Um compromisso do dia, já com a hora resolvida. */
    public record CompromissoDoDia(Lembrete lembrete, LocalDateTime quando) {

        public boolean jaPassou() {
            return quando.isBefore(LocalDateTime.now());
        }

        /** Está para acontecer na próxima hora? */
        public boolean eIminente() {
            LocalDateTime agora = LocalDateTime.now();
            return !quando.isBefore(agora) && quando.isBefore(agora.plusHours(1));
        }
    }

    /**
     * Tudo o que acontece hoje, em ordem de horário.
     *
     * Inclui o que já passou: saber que a reunião das 9h passou é tão útil
     * quanto saber da próxima — some da lista e some da cabeça.
     */
    public List<CompromissoDoDia> agendaDeHoje() {
        LocalDate hoje = LocalDate.now();
        return ocorrenciasEntre(hoje.atStartOfDay(), hoje.atTime(23, 59, 59));
    }

    /** O que vem depois de hoje, para os próximos dias. */
    public List<CompromissoDoDia> proximosDias(int dias) {
        LocalDate amanha = LocalDate.now().plusDays(1);
        return ocorrenciasEntre(amanha.atStartOfDay(),
                amanha.plusDays(Math.max(0, dias - 1)).atTime(23, 59, 59));
    }

    private List<CompromissoDoDia> ocorrenciasEntre(LocalDateTime de, LocalDateTime ate) {
        List<CompromissoDoDia> lista = new ArrayList<>();
        for (Lembrete lembrete : lembreteDao.listarAtivos()) {
            for (LocalDateTime quando : CalculadoraOcorrencias.entre(lembrete, de, ate)) {
                lista.add(new CompromissoDoDia(lembrete, quando));
            }
        }
        lista.sort(Comparator.comparing(CompromissoDoDia::quando));
        return lista;
    }

    /** Quantos compromissos ainda estão por vir hoje. */
    public long quantosFaltamHoje() {
        return agendaDeHoje().stream().filter(c -> !c.jaPassou()).count();
    }

    // ------------------------------------------------------------- recados

    public List<Recado> listarRecados() {
        return recadoDao.listar();
    }

    public Recado salvarRecado(Recado recado) {
        String erro = recado.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (recado.getId() == null) {
            recado.setOrdem(recadoDao.proximaOrdem());
        }
        Recado salvo = recadoDao.salvar(recado);
        EventBus.publicar(new RecadosMudaram());
        return salvo;
    }

    public void excluirRecado(long id) {
        recadoDao.excluir(id);
        EventBus.publicar(new RecadosMudaram());
    }

    /**
     * Move um recado para outra posição.
     *
     * Recebe a lista já na ordem desejada pela tela; aqui só se grava. Fazer
     * a conta de posições aqui, e não na interface, mantém a tela boba e a
     * regra em um lugar só.
     */
    public void reordenar(List<Recado> naNovaOrdem) {
        recadoDao.gravarOrdem(naNovaOrdem);
        EventBus.publicar(new RecadosMudaram());
    }

    /** Troca a cor sem abrir o editor. */
    public void trocarCor(Recado recado, CorRecado cor) {
        recado.setCor(cor);
        recadoDao.salvar(recado);
        EventBus.publicar(new RecadosMudaram());
    }
}
