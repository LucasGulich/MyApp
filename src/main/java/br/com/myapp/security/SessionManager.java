package br.com.myapp.security;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import javafx.scene.Scene;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tranca o aplicativo sozinho depois de um tempo sem uso.
 *
 * Qualquer toque no teclado ou no mouse dentro da janela conta como atividade.
 * Passado o limite configurado, a chave e descartada da memória e a tela de
 * bloqueio volta - Útil quando você sai da mesa e o computador fica aberto.
 */
public final class SessionManager {

    private static volatile Instant ultimaAtividade = Instant.now();
    private static ScheduledExecutorService vigia;

    private SessionManager() {
    }

    /** Passa a observar a atividade do usuário nesta janela. */
    public static void observar(Scene cena) {
        cena.addEventFilter(javafx.scene.input.InputEvent.ANY, e -> registrarAtividade());
    }

    public static void registrarAtividade() {
        ultimaAtividade = Instant.now();
    }

    /** Liga a vigia. Verifica de minuto em minuto. */
    public static synchronized void iniciar() {
        if (vigia != null) {
            return;
        }
        vigia = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread t = new Thread(tarefa, "vigia-inatividade");
            t.setDaemon(true);
            return t;
        });
        vigia.scheduleWithFixedDelay(SessionManager::verificar, 1, 1, TimeUnit.MINUTES);
    }

    public static synchronized void parar() {
        if (vigia != null) {
            vigia.shutdownNow();
            vigia = null;
        }
    }

    private static void verificar() {
        try {
            int limite = Config.get().minutosParaBloquear;
            if (limite <= 0 || !SecurityService.estaDestrancado()) {
                return;   // recurso desligado, ou já esta trancado
            }
            long parado = Duration.between(ultimaAtividade, Instant.now()).toMinutes();
            if (parado >= limite) {
                Log.info("Bloqueio automático após " + parado + " min sem uso.");
                SecurityService.trancar();
            }
        } catch (Exception e) {
            Log.erro("Falha na verificação de inatividade", e);
        }
    }

    /** Minutos desde a última interação. Usado pela tela de configurações. */
    public static long minutosParado() {
        return Duration.between(ultimaAtividade, Instant.now()).toMinutes();
    }
}
