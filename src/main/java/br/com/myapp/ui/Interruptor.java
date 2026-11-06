package br.com.myapp.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Chave liga/desliga, no lugar de uma caixa de seleção.
 *
 * <p>Para opção que <b>liga ou desliga algo agora</b> — um módulo no menu, um
 * bloco numa tela —, a chave diz o estado melhor que o ✓: a bolinha de um
 * lado ou do outro e a cor do trilho se leem de longe. A caixa de seleção
 * continua servindo para marcar itens de uma lista.
 *
 * <p>Clique, Espaço ou Enter alternam. {@link #travar()} deixa a chave ligada,
 * esmaecida e sem responder, para o que está sempre ativo — sem usar
 * {@code setDisable}, que apagaria também a dica do balão.
 */
public class Interruptor extends StackPane {

    private static final double LARGURA = 36;
    private static final double ALTURA = 20;
    private static final double BOLINHA = 14;
    private static final double CURSO = (LARGURA - BOLINHA) / 2 - 3;

    private final BooleanProperty ligado = new SimpleBooleanProperty(this, "ligado", false);
    private final Region bolinha = new Region();
    private final TranslateTransition deslize = new TranslateTransition(Duration.millis(120), bolinha);
    private boolean travado;

    /** Só a troca feita por quem usa desliza; a que vem do código (carregar a tela) já nasce no lugar. */
    private boolean animarProxima;

    public Interruptor() {
        getStyleClass().add("interruptor");
        bolinha.getStyleClass().add("interruptor-bolinha");
        bolinha.setMinSize(BOLINHA, BOLINHA);
        bolinha.setMaxSize(BOLINHA, BOLINHA);
        getChildren().add(bolinha);
        setAlignment(Pos.CENTER);
        setMinSize(LARGURA, ALTURA);
        setMaxSize(LARGURA, ALTURA);
        setPrefSize(LARGURA, ALTURA);
        setFocusTraversable(true);

        // Consumido para a linha em volta, que também alterna ao clique, não
        // desfazer a troca logo em seguida.
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            alternar();
            e.consume();
        });
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                alternar();
                e.consume();
            }
        });

        ligado.addListener((obs, antes, agora) -> desenhar(animarProxima));
        desenhar(false);
    }

    /** Inverte o estado, a menos que a chave esteja travada. */
    public void alternar() {
        if (!travado) {
            animarProxima = true;
            setLigado(!isLigado());
            animarProxima = false;
        }
    }

    /** Ligada, esmaecida e sem responder: está sempre ativo e não dá para mudar. */
    public Interruptor travar() {
        setLigado(true);
        travado = true;
        setFocusTraversable(false);
        getStyleClass().add("travado");
        return this;
    }

    public boolean isTravado() {
        return travado;
    }

    public boolean isLigado() {
        return ligado.get();
    }

    public void setLigado(boolean valor) {
        ligado.set(valor);
    }

    public BooleanProperty ligadoProperty() {
        return ligado;
    }

    private void desenhar(boolean animar) {
        getStyleClass().remove("ligado");
        if (isLigado()) {
            getStyleClass().add("ligado");
        }
        double destino = isLigado() ? CURSO : -CURSO;
        if (animar) {
            deslize.stop();
            deslize.setToX(destino);
            deslize.playFromStart();
        } else {
            deslize.stop();
            bolinha.setTranslateX(destino);
        }
    }
}
