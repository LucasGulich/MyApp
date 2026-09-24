package br.com.myapp.modules.kanban;

import br.com.myapp.modules.lembretes.ConversorData;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Janela de criação e edição de um cartão.
 *
 * <p>Segue o mesmo desenho do editor de lembrete: blocos com título, em vez
 * de uma coluna única de campos. São cinco assuntos diferentes — o que é, o
 * prazo, a cor, e a proteção — e agrupá-los faz o formulário ser lido como
 * tópicos.
 */
public class EditorCard {

    private final Window dono;
    private final CardKanban card;
    private final boolean ehNovo;

    private final TextField campoTitulo = new TextField();
    private final TextArea campoDescricao = new TextArea();
    private final DatePicker campoPrazo = new DatePicker();
    private final ToggleGroup grupoCores = new ToggleGroup();
    private final CheckBox campoProtegido =
            new CheckBox("Proteger este cartão com a senha mestra");

    public EditorCard(Window dono, long colunaId, CardKanban existente) {
        this.dono = dono;
        this.ehNovo = existente == null;
        this.card = existente == null ? new CardKanban() : existente;
        if (ehNovo) {
            this.card.setColunaId(colunaId);
        }
    }

    /** Abre a janela e devolve o cartão preenchido, ou vazio se cancelou. */
    public Optional<CardKanban> abrir() {
        Dialog<CardKanban> dialogo = new Dialog<>();
        dialogo.setTitle(ehNovo ? "Novo cartão" : "Editar cartão");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);

        ButtonType salvar = new ButtonType(ehNovo ? "Criar cartão" : "Salvar",
                ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);

        ScrollPane rolagem = new ScrollPane(montar());
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem-formulario");
        rolagem.setPrefViewportWidth(600);
        rolagem.setPrefViewportHeight(560);
        dialogo.getDialogPane().setContent(rolagem);

        Dialogos.aplicarTema(dialogo.getDialogPane());

        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");

        carregar();
        dialogo.setResultConverter(botao -> botao == salvar ? coletar() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }

    // ------------------------------------------------------------ formulário

    private VBox montar() {
        VBox forma = new VBox(18,
                secaoOQueE(),
                secaoPrazo(),
                secaoCor(),
                secaoSeguranca());
        forma.getStyleClass().add("formulario");
        return forma;
    }

    private Secao secaoOQueE() {
        campoTitulo.setPromptText("Ex.: Conferir a fila de integração do cliente X");
        campoTitulo.getStyleClass().addAll("campo", "campo-grande");

        campoDescricao.setPromptText("Detalhes, passos, links, o que for útil...");
        campoDescricao.getStyleClass().add("campo");
        campoDescricao.setWrapText(true);
        campoDescricao.setPrefRowCount(6);

        return new Secao(Icone.Simbolo.TEXTO, "O QUE É",
                Secao.comRotulo("Título", campoTitulo),
                Secao.comRotulo("Descrição", campoDescricao));
    }

    private Secao secaoPrazo() {
        campoPrazo.setPromptText("dd/mm/aaaa");
        campoPrazo.getStyleClass().add("campo");
        campoPrazo.setPrefWidth(200);
        campoPrazo.setConverter(ConversorData.BRASILEIRO);

        Button limpar = Botoes.link("sem prazo");
        limpar.setOnAction(e -> campoPrazo.setValue(null));

        HBox linha = new HBox(12, campoPrazo, limpar);
        linha.setAlignment(Pos.CENTER_LEFT);

        return new Secao(Icone.Simbolo.CALENDARIO, "PRAZO",
                linha,
                Secao.dica("Cartão com prazo vencido fica marcado em vermelho; "
                        + "com prazo para hoje, em âmbar. Prazo é opcional."));
    }

    private Secao secaoCor() {
        FlowPane cores = new FlowPane(8, 8);
        for (CorKanban cor : CorKanban.values()) {
            cores.getChildren().add(botaoDeCor(cor));
        }

        return new Secao(Icone.Simbolo.PONTO, "ETIQUETA DE COR",
                cores,
                Secao.dica("A cor vira um risco na lateral do cartão. "
                        + "Use para separar o que foge da rotina — se tudo tiver cor, "
                        + "nada se destaca."));
    }

    private ToggleButton botaoDeCor(CorKanban cor) {
        ToggleButton botao = new ToggleButton();
        botao.getStyleClass().add("chip-cor");
        botao.setToggleGroup(grupoCores);
        botao.setUserData(cor);
        Botoes.instalarDica(botao, cor.rotulo());

        if (cor.temCor()) {
            botao.setStyle("-fx-background-color: " + cor.faixa() + ";");
        } else {
            botao.setGraphic(Icone.de(Icone.Simbolo.FECHAR, 16, "icone-fraco"));
        }
        return botao;
    }

    private Secao secaoSeguranca() {
        campoProtegido.setDisable(!SecurityService.estaDestrancado());
        if (!SecurityService.estaDestrancado()) {
            campoProtegido.setText(
                    "Proteger com a senha mestra (destranque o aplicativo para usar)");
        }

        return new Secao(Icone.Simbolo.ESCUDO, "SEGURANÇA",
                campoProtegido,
                Secao.dica("Título e descrição ficam cifrados no banco. Com o "
                        + "aplicativo trancado, o cartão continua no lugar — só o "
                        + "texto some."));
    }

    // -------------------------------------------------------------- valores

    private void carregar() {
        campoTitulo.setText(card.getTitulo());
        campoDescricao.setText(card.getDescricao());
        campoPrazo.setValue(card.getPrazo());
        campoProtegido.setSelected(card.isProtegido());

        grupoCores.getToggles().stream()
                .filter(t -> t.getUserData() == card.getCor())
                .findFirst()
                .ifPresent(t -> t.setSelected(true));
    }

    private CardKanban coletar() {
        card.setTitulo(campoTitulo.getText());
        card.setDescricao(campoDescricao.getText());
        card.setPrazo(campoPrazo.getValue());
        card.setProtegido(campoProtegido.isSelected());

        if (grupoCores.getSelectedToggle() != null) {
            card.setCor((CorKanban) grupoCores.getSelectedToggle().getUserData());
        }
        return card;
    }
}
