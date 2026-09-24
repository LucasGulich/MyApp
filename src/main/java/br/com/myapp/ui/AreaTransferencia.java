package br.com.myapp.ui;

import br.com.myapp.core.Log;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cópia para a área de transferência, com limpeza automática.
 *
 * Copiar uma senha e esquecê-la na área de transferência é um jeito comum de
 * vazá-la: basta um Ctrl+V distraído em um chat. Aqui, a cópia de um segredo
 * se apaga sozinha depois de alguns segundos.
 *
 * A limpeza é conservadora: só apaga se o que estiver lá ainda for o mesmo
 * texto que este aplicativo colocou. Se você já copiou outra coisa no meio do
 * caminho, o aplicativo não mexe.
 */
public final class AreaTransferencia {

    /** Quanto tempo um segredo pode ficar disponível para colar. */
    public static final int SEGUNDOS_ATE_LIMPAR = 30;

    private static final ScheduledExecutorService AGENDA =
            Executors.newSingleThreadScheduledExecutor(tarefa -> {
                Thread t = new Thread(tarefa, "limpeza-area-transferencia");
                t.setDaemon(true);
                return t;
            });

    private AreaTransferencia() {
    }

    /** Copia um texto comum, que permanece disponível. */
    public static void copiar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        ClipboardContent conteudo = new ClipboardContent();
        conteudo.putString(texto);
        Clipboard.getSystemClipboard().setContent(conteudo);
    }

    /**
     * Copia um segredo e agenda a limpeza.
     *
     * @return por quantos segundos o valor ficará disponível
     */
    public static int copiarSegredo(String segredo) {
        if (segredo == null || segredo.isEmpty()) {
            return 0;
        }
        copiar(segredo);

        AGENDA.schedule(() -> Platform.runLater(() -> {
            try {
                Clipboard prancheta = Clipboard.getSystemClipboard();
                // Só limpa se ninguém copiou outra coisa nesse meio tempo.
                if (segredo.equals(prancheta.getString())) {
                    prancheta.clear();
                    Log.info("Área de transferência limpa automaticamente.");
                }
            } catch (Exception e) {
                Log.aviso("Falha ao limpar a área de transferência: " + e.getMessage());
            }
        }), SEGUNDOS_ATE_LIMPAR, TimeUnit.SECONDS);

        return SEGUNDOS_ATE_LIMPAR;
    }

    /** Limpa agora, sem esperar. Usado quando o aplicativo tranca. */
    public static void limparAgora() {
        Platform.runLater(() -> {
            try {
                Clipboard.getSystemClipboard().clear();
            } catch (Exception ignorado) {
                // Sem prancheta disponível: nada a fazer.
            }
        });
    }
}
