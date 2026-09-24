package br.com.myapp.core;

import br.com.myapp.modules.lembretes.CalculadoraOcorrencias;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteDao;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Motor de agendamento do aplicativo.
 *
 * Substitui o schtasks do Lembrete.bat. A cada varredura ele pergunta
 * "quais avisos deveriam ter saído entre a última verificação e agora?",
 * dispara os que faltam e anota o que já disparou.
 *
 * Duas consequências práticas desse desenho:
 *
 *  - Nada e perdido. Se o computador estava desligado ou o aplicativo fechado,
 *    a próxima abertura varre o período inteiro e mostra o que ficou para trás
 *    (limitado pela configuração diasDeCatchUp).
 *
 *  - Nada e repetido. Cada par (ocorrência, antecedência) só dispara uma vez,
 *    garantido por uma restrição de unicidade no banco.
 */
public final class Scheduler {

    /** De quanto em quanto tempo a agenda e varrida. */
    private static final int SEGUNDOS_ENTRE_VARREDURAS = 20;

    /** Acima disso, o aviso e tratado como atrasado e vai para a lista de perdidos. */
    private static final int MINUTOS_PARA_CONSIDERAR_ATRASADO = 5;

    /** Evento publicado quando um aviso deve aparecer na tela. */
    public record AlertaDisparado(Lembrete lembrete, LocalDateTime ocorrencia, int antecedencia,
                                  boolean atrasado) {

        /** Texto pronto para o título do alerta. */
        public String resumoTempo() {
            if (antecedencia == 0) {
                return "Agora";
            }
            if (antecedencia < 60) {
                return "Em " + antecedencia + " minutos";
            }
            if (antecedencia % 1440 == 0) {
                int dias = antecedencia / 1440;
                return dias == 1 ? "Amanhã" : "Em " + dias + " dias";
            }
            if (antecedencia % 60 == 0) {
                int horas = antecedencia / 60;
                return horas == 1 ? "Em 1 hora" : "Em " + horas + " horas";
            }
            return "Em " + (antecedencia / 60) + "h" + (antecedencia % 60) + "min";
        }
    }

    private static ScheduledExecutorService executor;
    private static final LembreteDao DAO = new LembreteDao();

    private Scheduler() {
    }

    /** Liga o agendador. Faz a primeira varredura logo em seguida. */
    public static synchronized void iniciar() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread t = new Thread(tarefa, "agendador");
            t.setDaemon(true);   // Não segura o encerramento do aplicativo
            return t;
        });
        executor.scheduleWithFixedDelay(Scheduler::varrer, 2, SEGUNDOS_ENTRE_VARREDURAS, TimeUnit.SECONDS);
        Log.info("Agendador iniciado (varredura a cada " + SEGUNDOS_ENTRE_VARREDURAS + "s).");
    }

    public static synchronized void parar() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
            Log.info("Agendador parado.");
        }
    }

    /** Forca uma varredura imediata (usado depois de salvar um lembrete). */
    public static void varrerAgora() {
        if (executor != null) {
            executor.execute(Scheduler::varrer);
        }
    }

    // ------------------------------------------------------------ varredura

    private static void varrer() {
        try {
            Config config = Config.get();
            LocalDateTime agora = LocalDateTime.now();

            LocalDateTime inicioDaJanela = calcularInicioDaJanela(config, agora);

            for (Lembrete lembrete : DAO.listarAtivos()) {
                verificar(lembrete, inicioDaJanela, agora);
            }
            processarAdiamentos();

            config.ultimaVarreduraMillis = paraMillis(agora);
            config.salvar();
        } catch (Exception e) {
            // Uma falha aqui não pode matar a thread do agendador: sem ela o
            // aplicativo ficaria mudo sem qualquer sinal para o usuário.
            Log.erro("Falha na varredura da agenda", e);
        }
    }

    /**
     * Onde começa a janela de verificação.
     *
     * Normalmente e a última varredura. Na primeira execução, ou depois de
     * muito tempo fechado, recua no máximo o que a configuração permitir.
     */
    private static LocalDateTime calcularInicioDaJanela(Config config, LocalDateTime agora) {
        LocalDateTime limiteDeCatchUp = agora.minusDays(Math.max(0, config.diasDeCatchUp));

        if (config.ultimaVarreduraMillis <= 0) {
            // Primeira vez: Não inventa histórico, começa de agora.
            return agora.minusMinutes(1);
        }
        LocalDateTime ultima = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(config.ultimaVarreduraMillis), ZoneId.systemDefault());

        return ultima.isBefore(limiteDeCatchUp) ? limiteDeCatchUp : ultima;
    }

    /**
     * Verifica um lembrete.
     *
     * Um aviso sai em (ocorrência - Antecedência). Para saber quais avisos
     * caem na janela, deslocamos a janela para a frente pela antecedência e
     * perguntamos quais ocorrências caem ali.
     */
    private static void verificar(Lembrete lembrete, LocalDateTime de, LocalDateTime ate) {
        if (lembrete.getId() == null) {
            return;
        }
        for (int antecedencia : lembrete.getAntecedencias()) {
            List<LocalDateTime> ocorrencias = CalculadoraOcorrencias.entre(
                    lembrete,
                    de.plusMinutes(antecedencia),
                    ate.plusMinutes(antecedencia));

            for (LocalDateTime ocorrencia : ocorrencias) {
                if (DAO.jaDisparou(lembrete.getId(), ocorrencia, antecedencia)) {
                    continue;
                }
                if (!DAO.registrarDisparo(lembrete.getId(), ocorrencia, antecedencia)) {
                    continue;   // outra varredura ganhou a corrida
                }

                LocalDateTime instanteDoAviso = ocorrencia.minusMinutes(antecedencia);
                boolean atrasado = Duration.between(instanteDoAviso, ate).toMinutes()
                        > MINUTOS_PARA_CONSIDERAR_ATRASADO;

                Log.info("Alerta disparado: lembrete=" + lembrete.getId()
                        + " Antecedência=" + antecedencia + "min"
                        + (atrasado ? " (atrasado)" : ""));

                EventBus.publicar(new AlertaDisparado(lembrete, ocorrencia, antecedencia, atrasado));
            }
        }
    }

    /** Reapresenta os alertas que você mandou adiar. */
    private static void processarAdiamentos() {
        for (LembreteDao.Disparo adiado : DAO.adiamentosVencidos()) {
            DAO.porId(adiado.lembreteId()).ifPresent(lembrete ->
                    EventBus.publicar(new AlertaDisparado(
                            lembrete, adiado.ocorrencia(), adiado.antecedencia(), false)));
        }
    }

    private static long paraMillis(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
