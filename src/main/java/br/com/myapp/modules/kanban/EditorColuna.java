package br.com.myapp.modules.kanban;

import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Janela de criação e renomeação de uma coluna.
 *
 * <p>Uma coluna tem duas coisas: nome e cor. O formulário cabe em um bloco
 * só, e por isso não usa seções separadas como o editor de lembrete — dividir
 * dois campos em tópicos seria burocracia sem ganho.
 */
public class EditorColuna {

    private final Window dono;
    private final ColunaKanban coluna;
    private final boolean ehNova;

    private final TextField campoNome = new TextField();
    private final ToggleGroup grupoCores = new ToggleGroup();

    public EditorColuna(Window dono, ColunaKanban existente) {
        this.dono = dono;
        this.ehNova = existente == null;
        this.coluna = existente == null ? new ColunaKanban() : existente;
    }

    /** Abre a janela e devolve a coluna preenchida, ou vazio se cancelou. */
    public Optional<ColunaKanban> abrir() {
        Dialog<ColunaKanban> dialogo = new Dialog<>();
        dialogo.setTitle(ehNova ? "Nova coluna" : "Renomear coluna");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        ButtonType salvar = new ButtonType(ehNova ? "Criar coluna" : "Salvar",
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
        campoNome.setPromptText("Ex.: A fazer, Em andamento, Concluído...");
        campoNome.getStyleClass().addAll("campo", "campo-grande");

        Secao secao = new Secao(Icone.Simbolo.ORDENAR, "A COLUNA",
                Secao.comRotulo("Nome", campoNome),
                Secao.comRotulo("Cor", montarCores()),
                Secao.dica("A cor aparece como uma linha no topo da coluna. "
                        + "Serve para separar frentes de trabalho de relance."));

        VBox forma = new VBox(18, secao);
        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(460);
        return forma;
    }

    private FlowPane montarCores() {
        FlowPane cores = new FlowPane(8, 8);
        for (CorKanban cor : CorKanban.values()) {
            cores.getChildren().add(botaoDeCor(cor));
        }
        return cores;
    }

    private ToggleButton botaoDeCor(CorKanban cor) {
        ToggleButton botao = new ToggleButton();
        botao.getStyleClass().add("chip-cor");
        botao.setToggleGroup(grupoCores);
        botao.setUserData(cor);
        br.com.myapp.ui.Botoes.instalarDica(botao, cor.rotulo());

        if (cor.temCor()) {
            botao.setStyle("-fx-background-color: " + cor.faixa() + ";");
        } else {
            // "Sem cor" não pode ser um quadrado vazio, que se confunde com um
            // botão desligado: ganha o ícone de proibido.
            botao.setGraphic(Icone.de(Icone.Simbolo.FECHAR, 16, "icone-fraco"));
        }
        return botao;
    }

    private void carregar() {
        campoNome.setText(coluna.getNome());
        grupoCores.getToggles().stream()
                .filter(t -> t.getUserData() == coluna.getCor())
                .findFirst()
                .ifPresent(t -> t.setSelected(true));
    }

    private ColunaKanban coletar() {
        coluna.setNome(campoNome.getText());
        if (grupoCores.getSelectedToggle() != null) {
            coluna.setCor((CorKanban) grupoCores.getSelectedToggle().getUserData());
        }
        return coluna;
    }
}
