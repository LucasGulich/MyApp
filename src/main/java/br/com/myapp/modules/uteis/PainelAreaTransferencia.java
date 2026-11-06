package br.com.myapp.modules.uteis;

import br.com.myapp.core.Log;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Limpar o Ctrl+C, ou soterrar o histórico dele.
 *
 * <p><b>Limpar</b> esvazia o que está copiado agora: o Ctrl+V seguinte não
 * cola nada. <b>Sobrescrever</b> copia ".", "..", "..." e assim por diante,
 * para empurrar para fora o histórico do Windows (Win+V) — ele guarda os
 * últimos 25 itens, e o que entra por último expulsa o mais velho.
 *
 * <p><b>Cada cópia é um texto diferente</b> porque o histórico não guarda
 * repetidos: copiar de novo algo que já está lá só sobe o item para o topo.
 * Com "." e ".." alternados (a primeira versão), o histórico ganhava só dois
 * itens e o resto ficava lá. A pausa entre as cópias é para o Windows
 * registrar cada uma.
 */
public class PainelAreaTransferencia extends Secao {

    /** O tamanho do histórico do Windows: com isso, nenhum item antigo sobra. */
    static final int COPIAS = 25;

    /** Pausa entre uma cópia e outra, para o histórico registrar cada uma. */
    // 150 ms era rápido demais: o histórico do Windows deixava passar parte
    // das cópias (relato do usuário, v1.13.1). Com 700 ms, as 25 levam uns
    // 18 s — devagar, mas o Win+V registra todas.
    static final Duration PAUSA = Duration.millis(700);

    private final Button limpar = Botoes.comum("Limpar o que está copiado", Icone.Simbolo.EXCLUIR);
    private final Button sobrescrever = Botoes.comum("Sobrescrever o histórico", Icone.Simbolo.REPETIR);
    private final ProgressBar barra = new ProgressBar(0);
    private final Label andamento = new Label();

    public PainelAreaTransferencia() {
        super(Icone.Simbolo.COPIAR, "ÁREA DE TRANSFERÊNCIA (CTRL+C / CTRL+V)",
                "Para não deixar senha ou dado de cliente para trás no Ctrl+V nem no histórico do Win+V.");

        Botoes.instalarDica(limpar, "Esvazia o que está copiado agora. O próximo Ctrl+V não cola nada.");
        Botoes.instalarDica(sobrescrever, "Copia \".\", \"..\", \"...\" e assim por diante — " + COPIAS + " textos diferentes, "
                + "para empurrar tudo o que estava no histórico do Win+V para fora.");
        limpar.setOnAction(e -> limpar());
        sobrescrever.setOnAction(e -> sobrescrever());

        barra.getStyleClass().add("barra-progresso");
        barra.setPrefWidth(160);
        andamento.getStyleClass().add("texto-fraco");
        mostrarAndamento(false);

        HBox botoes = new HBox(10, limpar, sobrescrever, barra, andamento);
        botoes.setAlignment(Pos.CENTER_LEFT);

        com(botoes, Secao.dica("Itens fixados no Win+V (o alfinete) não saem por nenhum dos dois: "
                + "o Windows guarda esses de propósito."));
    }

    /** ".", "..", "..." — {@code quantidade} textos, todos diferentes. */
    static List<String> sequencia(int quantidade) {
        List<String> textos = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            textos.add(".".repeat(i + 1));
        }
        return textos;
    }

    private void limpar() {
        try {
            Clipboard.getSystemClipboard().clear();
            Aviso.sucesso("Área de transferência limpa.");
        } catch (Exception e) {
            Log.aviso("Falha ao limpar a área de transferência: " + e.getMessage());
            Aviso.erro("Não consegui limpar a área de transferência.");
        }
    }

    private void sobrescrever() {
        List<String> textos = sequencia(COPIAS);
        int[] feitas = {0};

        limpar.setDisable(true);
        sobrescrever.setDisable(true);
        mostrarAndamento(true);
        barra.setProgress(0);

        // Timeline, e não um laço com sleep: a cópia precisa acontecer na
        // thread da tela, e a tela não pode congelar durante os segundos.
        Timeline copias = new Timeline(new KeyFrame(PAUSA, e -> {
            AreaTransferencia.copiar(textos.get(feitas[0]));
            feitas[0]++;
            barra.setProgress((double) feitas[0] / COPIAS);
            andamento.setText("Copiando " + feitas[0] + " de " + COPIAS + "…");
        }));
        copias.setCycleCount(COPIAS);
        copias.setOnFinished(e -> {
            limpar.setDisable(false);
            sobrescrever.setDisable(false);
            mostrarAndamento(false);
            Aviso.sucesso("Histórico sobrescrito com " + COPIAS + " cópias diferentes.");
        });
        copias.play();
    }

    private void mostrarAndamento(boolean visivel) {
        barra.setVisible(visivel);
        barra.setManaged(visivel);
        andamento.setVisible(visivel);
        andamento.setManaged(visivel);
    }
}
