package br.com.myapp.ui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Barra de título desenhada pelo aplicativo.
 *
 * A barra que o Windows desenha não acompanha o tema do programa: em uma
 * janela escura ela aparecia branca, destoando de tudo. Existe uma API do
 * sistema para escurecê-la, mas ela depende da versão do Windows e de chamada
 * nativa — e ainda assim ficaria só "escura", nunca com as cores daqui.
 *
 * Desenhar a própria barra resolve os dois lados: combina com o tema claro e
 * com o escuro, e não depende de nada do sistema. Em troca, é preciso
 * reimplementar o que a barra nativa fazia de graça — arrastar, maximizar com
 * duplo clique e os três botões — que é o que esta classe faz.
 */
public class BarraTitulo extends HBox {

    /** Onde o cursor estava quando o arrasto começou. */
    private double deslocamentoX;
    private double deslocamentoY;

    /** Posição e tamanho antes de maximizar, para conseguir restaurar. */
    private double antesX;
    private double antesY;
    private double antesLargura;
    private double antesAltura;
    private boolean maximizada;

    private final Stage janela;
    private final Button botaoMaximizar;

    /**
     * @param janela     a janela controlada
     * @param titulo     texto exibido
     * @param comIcone   mostra o ícone do aplicativo à esquerda
     * @param permiteMaximizar janelas de diálogo passam false
     */
    public BarraTitulo(Stage janela, String titulo, boolean comIcone, boolean permiteMaximizar) {
        this.janela = janela;
        getStyleClass().add("barra-titulo");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        if (comIcone) {
            ImageView icone = new ImageView(IconeApp.paraJanela().get(2));   // 24 px
            icone.setFitWidth(18);
            icone.setFitHeight(18);
            icone.setPreserveRatio(true);
            getChildren().add(icone);
        }

        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("barra-titulo-texto");
        getChildren().add(rotulo);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        getChildren().add(espaco);

        Button minimizar = botao(Icone.Simbolo.JANELA_MINIMIZAR, "Minimizar");
        minimizar.setOnAction(e -> janela.setIconified(true));

        botaoMaximizar = botao(Icone.Simbolo.JANELA_MAXIMIZAR, "Maximizar");
        botaoMaximizar.setOnAction(e -> alternarMaximizada());

        Button fechar = botao(Icone.Simbolo.FECHAR, "Fechar");
        fechar.getStyleClass().add("botao-janela-fechar");
        fechar.setOnAction(e -> janela.fireEvent(new javafx.stage.WindowEvent(
                janela, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));

        if (permiteMaximizar) {
            getChildren().addAll(minimizar, botaoMaximizar, fechar);
        } else {
            getChildren().addAll(minimizar, fechar);
        }

        ligarArrasto(permiteMaximizar);
    }

    /** Barra simplificada para diálogos: sem ícone e sem maximizar. */
    public static BarraTitulo paraDialogo(Stage janela, String titulo) {
        return new BarraTitulo(janela, titulo, false, false);
    }

    private Button botao(Icone.Simbolo simbolo, String dica) {
        Button b = new Button();
        b.getStyleClass().add("botao-janela");
        b.setGraphic(Icone.de(simbolo, 15));
        b.setTooltip(new javafx.scene.control.Tooltip(dica));
        b.setFocusTraversable(false);
        return b;
    }

    // ------------------------------------------------------------- arrasto

    private void ligarArrasto(boolean permiteMaximizar) {
        setOnMousePressed(evento -> {
            deslocamentoX = evento.getSceneX();
            deslocamentoY = evento.getSceneY();
        });

        setOnMouseDragged(evento -> {
            if (evento.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            // Arrastar uma janela maximizada primeiro a restaura, como no
            // Windows, e a reposiciona sob o cursor.
            if (maximizada) {
                double proporcao = deslocamentoX / getWidth();
                restaurar();
                deslocamentoX = proporcao * janela.getWidth();
            }
            janela.setX(evento.getScreenX() - deslocamentoX);
            janela.setY(evento.getScreenY() - deslocamentoY);
        });

        if (permiteMaximizar) {
            setOnMouseClicked(evento -> {
                if (evento.getClickCount() == 2
                        && evento.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    alternarMaximizada();
                }
            });
        }
    }

    // ---------------------------------------------------------- maximizar

    private void alternarMaximizada() {
        if (maximizada) {
            restaurar();
        } else {
            maximizar();
        }
    }

    private void maximizar() {
        antesX = janela.getX();
        antesY = janela.getY();
        antesLargura = janela.getWidth();
        antesAltura = janela.getHeight();

        // getVisualBounds respeita a barra de tarefas: usar getBounds cobriria
        // a barra e esconderia o relógio e a bandeja.
        Rectangle2D area = telaAtual().getVisualBounds();
        janela.setX(area.getMinX());
        janela.setY(area.getMinY());
        janela.setWidth(area.getWidth());
        janela.setHeight(area.getHeight());

        maximizada = true;
        botaoMaximizar.setGraphic(Icone.de(Icone.Simbolo.JANELA_RESTAURAR, 15));
        botaoMaximizar.getTooltip().setText("Restaurar");
    }

    private void restaurar() {
        janela.setX(antesX);
        janela.setY(antesY);
        janela.setWidth(antesLargura);
        janela.setHeight(antesAltura);

        maximizada = false;
        botaoMaximizar.setGraphic(Icone.de(Icone.Simbolo.JANELA_MAXIMIZAR, 15));
        botaoMaximizar.getTooltip().setText("Maximizar");
    }

    /** O monitor onde a janela está, para maximizar no lugar certo. */
    private Screen telaAtual() {
        return Screen.getScreensForRectangle(
                        janela.getX(), janela.getY(), janela.getWidth(), janela.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary());
    }

    public boolean estaMaximizada() {
        return maximizada;
    }
}
