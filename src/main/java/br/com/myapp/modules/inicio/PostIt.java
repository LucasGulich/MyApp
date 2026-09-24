package br.com.myapp.modules.inicio;

import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Icone;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * O recado desenhado na tela — o papel adesivo.
 *
 * A aparência imita o bloco de papel: faixa mais escura em cima (a parte
 * colada), corpo na cor escolhida, texto em tom escuro e uma leve sombra. A
 * cor vem do enum, e não do CSS, porque são oito e cada uma tem três tons —
 * uma regra de estilo por tom seriam vinte e quatro regras quase iguais.
 */
public class PostIt extends VBox {

    private static final int LARGURA = 210;

    private final Recado recado;

    public PostIt(Recado recado,
                  Consumer<Recado> aoEditar,
                  Consumer<Recado> aoExcluir,
                  TrocaDeCor aoTrocarCor) {
        this.recado = recado;

        getStyleClass().add("post-it");
        setPrefWidth(LARGURA);
        setMinWidth(LARGURA);
        setMaxWidth(LARGURA);
        setSpacing(0);

        aplicarCor(recado.getCor());

        getChildren().addAll(
                montarFaixa(aoExcluir),
                montarCorpo());

        // Clicar duas vezes abre para editar — é o gesto que a pessoa tenta.
        setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2) {
                aoEditar.accept(recado);
            }
        });

        // VBox não é um Control, então não tem setContextMenu: o menu é
        // aberto na mão, na posição do cursor.
        ContextMenu menu = montarMenu(aoEditar, aoExcluir, aoTrocarCor);
        setOnContextMenuRequested(evento -> {
            menu.show(this, evento.getScreenX(), evento.getScreenY());
            evento.consume();
        });

        Botoes.instalarDica(this, "Clique duas vezes para editar  •  "
                + "arraste para mudar de lugar  •  botão direito para mais opções");
    }

    /** A faixa de cima, que imita a parte colada do papel. */
    private HBox montarFaixa(Consumer<Recado> aoExcluir) {
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Button fechar = new Button();
        fechar.setGraphic(Icone.de(Icone.Simbolo.FECHAR, 12));
        fechar.getStyleClass().add("post-it-fechar");
        fechar.setFocusTraversable(false);
        fechar.setTooltip(new Tooltip("Tirar este recado"));
        fechar.setOnAction(e -> aoExcluir.accept(recado));

        HBox faixa = new HBox(espaco, fechar);
        faixa.getStyleClass().add("post-it-faixa");
        faixa.setAlignment(Pos.CENTER_RIGHT);
        faixa.setStyle("-fx-background-color: " + recado.getCor().faixa() + ";");
        return faixa;
    }

    private VBox montarCorpo() {
        Label texto = new Label(recado.getTexto());
        texto.getStyleClass().add("post-it-texto");
        texto.setWrapText(true);
        texto.setMaxWidth(LARGURA - 28);
        texto.setStyle("-fx-text-fill: " + recado.getCor().texto() + ";");

        VBox corpo = new VBox(texto);
        corpo.getStyleClass().add("post-it-corpo");
        return corpo;
    }

    private ContextMenu montarMenu(Consumer<Recado> aoEditar,
                                   Consumer<Recado> aoExcluir,
                                   TrocaDeCor aoTrocarCor) {
        ContextMenu menu = new ContextMenu();

        MenuItem editar = new MenuItem("Editar", Icone.de(Icone.Simbolo.EDITAR, 15));
        editar.setOnAction(e -> aoEditar.accept(recado));

        MenuItem excluir = new MenuItem("Tirar da tela",
                Icone.de(Icone.Simbolo.EXCLUIR, 15, "icone-perigo"));
        excluir.setOnAction(e -> aoExcluir.accept(recado));

        menu.getItems().add(editar);
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());

        for (CorRecado cor : CorRecado.values()) {
            MenuItem item = new MenuItem(
                    cor == recado.getCor() ? cor.rotulo() + "  (atual)" : cor.rotulo());
            // Um quadradinho da cor ao lado do nome, para escolher pelo olho.
            Region amostra = new Region();
            amostra.setMinSize(14, 14);
            amostra.setPrefSize(14, 14);
            amostra.setStyle("-fx-background-color: " + cor.fundo()
                    + "; -fx-background-radius: 3; -fx-border-color: " + cor.faixa()
                    + "; -fx-border-radius: 3;");
            item.setGraphic(amostra);
            item.setOnAction(e -> aoTrocarCor.trocar(recado, cor));
            menu.getItems().add(item);
        }

        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        menu.getItems().add(excluir);
        return menu;
    }

    private void aplicarCor(CorRecado cor) {
        setStyle("-fx-background-color: " + cor.fundo() + ";");
    }

    public Recado recado() {
        return recado;
    }

    /** Escolha de cor pelo menu do recado. */
    @FunctionalInterface
    public interface TrocaDeCor {
        void trocar(Recado recado, CorRecado cor);
    }
}
