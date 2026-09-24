package br.com.myapp.modules.inicio;

import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Janela para escrever um recado.
 *
 * Curta de propósito: um campo de texto e a escolha da cor. Se a anotação
 * precisar de mais que isso, ela não é um recado — é uma nota.
 */
public class EditorRecado {

    private final Window dono;
    private final Recado recado;
    private final boolean ehNovo;

    private final TextArea campoTexto = new TextArea();
    private final ToggleGroup grupoCores = new ToggleGroup();

    public EditorRecado(Window dono, Recado existente) {
        this.dono = dono;
        this.ehNovo = existente == null;
        this.recado = existente == null ? new Recado() : existente;
    }

    /** Abre a janela. Devolve o recado preenchido, ou vazio se cancelou. */
    public Optional<Recado> abrir() {
        Dialog<Recado> dialogo = new Dialog<>();
        dialogo.setTitle(ehNovo ? "Novo recado" : "Editar recado");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        ButtonType salvar = new ButtonType(ehNovo ? "Colar na tela" : "Salvar",
                ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);
        dialogo.getDialogPane().setContent(montar());

        Dialogos.aplicarTema(dialogo.getDialogPane());

        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");

        carregar();

        dialogo.setResultConverter(botao -> botao == salvar ? coletar() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }

    private VBox montar() {
        campoTexto.setPromptText("Ex.: ligar para o contador antes de sexta");
        campoTexto.getStyleClass().add("campo");
        campoTexto.setWrapText(true);
        campoTexto.setPrefRowCount(5);

        FlowPane cores = new FlowPane(10, 10);
        for (CorRecado cor : CorRecado.values()) {
            ToggleButton botao = new ToggleButton();
            botao.setToggleGroup(grupoCores);
            botao.setUserData(cor);
            botao.getStyleClass().add("chip-cor");
            botao.setTooltip(new javafx.scene.control.Tooltip(cor.rotulo()));
            // O botão é a própria amostra da cor.
            botao.setStyle("-fx-background-color: " + cor.fundo()
                    + "; -fx-border-color: " + cor.faixa() + ";");
            cores.getChildren().add(botao);
        }

        VBox forma = new VBox(18,
                new Secao(Icone.Simbolo.TEXTO, "O RECADO",
                        campoTexto,
                        Secao.dica("Bilhete rápido, do tamanho de um papel adesivo. "
                                + "Para textos longos, use as Notas.")),
                new Secao(Icone.Simbolo.PONTO, "COR", cores));

        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(460);
        return forma;
    }

    private void carregar() {
        campoTexto.setText(recado.getTexto());

        CorRecado atual = recado.getCor();
        grupoCores.getToggles().stream()
                .filter(t -> atual == t.getUserData())
                .findFirst()
                .ifPresentOrElse(
                        t -> t.setSelected(true),
                        () -> grupoCores.getToggles().get(0).setSelected(true));
    }

    private Recado coletar() {
        recado.setTexto(campoTexto.getText() == null ? "" : campoTexto.getText().trim());
        if (grupoCores.getSelectedToggle() != null) {
            recado.setCor((CorRecado) grupoCores.getSelectedToggle().getUserData());
        }
        return recado;
    }
}
