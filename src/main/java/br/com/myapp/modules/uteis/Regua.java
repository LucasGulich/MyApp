package br.com.myapp.modules.uteis;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * A régua de leitura: uma faixa translúcida que fica por cima de qualquer
 * programa, para marcar a linha de um relatório.
 *
 * <p>Não é um bloco de tela, é uma janela própria (sem moldura, transparente,
 * sempre no topo). O estado — ligada, tamanho, opacidade, cor — fica em
 * propriedades estáticas: o {@link PainelRegua} liga os controles a elas e a
 * janela as acompanha, sem um precisar conhecer o outro.
 *
 * <p>Na faixa: arrastar pelo meio move; arrastar pelas bordas redimensiona;
 * Esc ou o botão direito desligam. O tamanho, a cor e o lugar ficam gravados
 * em {@link Config} e voltam na próxima vez.
 *
 * <p><b>Limite conhecido:</b> a faixa captura o clique — o que está embaixo
 * dela não recebe o mouse enquanto ela estiver ali. Só a faixa, o resto da
 * tela segue normal.
 */
public final class Regua {

    /** As cores oferecidas, com o nome que aparece na tela. */
    public enum Cor {
        AMARELO("Amarelo", 255, 214, 10),
        VERDE("Verde", 52, 199, 89),
        AZUL("Azul", 76, 141, 255),
        ROSA("Rosa", 255, 105, 180),
        LARANJA("Laranja", 255, 149, 0);

        final String nome;
        final int r;
        final int g;
        final int b;

        Cor(String nome, int r, int g, int b) {
            this.nome = nome;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public String nome() {
            return nome;
        }

        static Cor de(String texto) {
            try {
                return valueOf(texto);
            } catch (RuntimeException e) {
                return AMARELO;
            }
        }
    }

    public static final double ALTURA_MIN = 12;
    public static final double ALTURA_MAX = 300;
    public static final double LARGURA_MIN = 120;
    public static final double OPACIDADE_MIN = 0.05;
    public static final double OPACIDADE_MAX = 0.9;

    /** A faixa sensível das bordas, em pixels. */
    private static final double BORDA = 6;

    private static final BooleanProperty ATIVA = new SimpleBooleanProperty(false);
    private static final DoubleProperty ALTURA = new SimpleDoubleProperty();
    private static final DoubleProperty LARGURA = new SimpleDoubleProperty();
    private static final DoubleProperty OPACIDADE = new SimpleDoubleProperty();
    private static final ObjectProperty<Cor> COR = new SimpleObjectProperty<>();

    private static Stage dono;
    private static Stage janela;
    private static StackPane faixa;

    static {
        Config config = Config.get();
        ALTURA.set(limitar(config.reguaAltura, ALTURA_MIN, ALTURA_MAX));
        LARGURA.set(Math.max(LARGURA_MIN, config.reguaLargura));
        OPACIDADE.set(limitar(config.reguaOpacidade, OPACIDADE_MIN, OPACIDADE_MAX));
        COR.set(Cor.de(config.reguaCor));

        ALTURA.addListener((o, antes, agora) -> acompanharTamanho());
        LARGURA.addListener((o, antes, agora) -> acompanharTamanho());
        OPACIDADE.addListener((o, antes, agora) -> pintar());
        COR.addListener((o, antes, agora) -> pintar());
    }

    private Regua() {
    }

    // ------------------------------------------------------- propriedades

    public static BooleanProperty ativa() {
        return ATIVA;
    }

    public static DoubleProperty altura() {
        return ALTURA;
    }

    public static DoubleProperty largura() {
        return LARGURA;
    }

    public static DoubleProperty opacidade() {
        return OPACIDADE;
    }

    public static ObjectProperty<Cor> cor() {
        return COR;
    }

    // ------------------------------------------------------------ ligar

    /** Liga se estava desligada, desliga se estava ligada. Só na thread do JavaFX. */
    public static void alternar() {
        if (ATIVA.get()) {
            desativar();
        } else {
            ativar();
        }
    }

    public static void ativar() {
        if (ATIVA.get()) {
            return;
        }
        try {
            if (janela == null) {
                montar();
            }
            janela.setWidth(LARGURA.get());
            janela.setHeight(ALTURA.get());
            posicionar();
            pintar();
            dono.show();
            janela.show();
            janela.toFront();
            ATIVA.set(true);
        } catch (Exception e) {
            Log.erro("Falha ao ligar a régua", e);
        }
    }

    public static void desativar() {
        if (!ATIVA.get()) {
            return;
        }
        guardar();
        janela.hide();
        dono.hide();
        ATIVA.set(false);
    }

    /** Grava tamanho, cor, opacidade e posição para a próxima vez. */
    public static void guardar() {
        Config config = Config.get();
        config.reguaAltura = (int) Math.round(ALTURA.get());
        config.reguaLargura = (int) Math.round(LARGURA.get());
        config.reguaOpacidade = Math.round(OPACIDADE.get() * 100) / 100.0;
        config.reguaCor = COR.get().name();
        if (janela != null && janela.isShowing()) {
            config.reguaPosicionada = true;
            config.reguaX = janela.getX();
            config.reguaY = janela.getY();
        }
        config.salvar();
    }

    // ----------------------------------------------------------- janela

    private static void montar() {
        // Uma janela solta, sem moldura, entraria na barra de tarefas. Com um
        // dono invisível do tipo UTILITY ela fica só como faixa, sem botão.
        dono = new Stage(StageStyle.UTILITY);
        dono.setOpacity(0);
        dono.setWidth(1);
        dono.setHeight(1);
        dono.setX(-32000);
        dono.setY(-32000);

        janela = new Stage(StageStyle.TRANSPARENT);
        janela.initOwner(dono);
        janela.setAlwaysOnTop(true);
        janela.setTitle("Régua");

        faixa = new StackPane();
        Scene cena = new Scene(faixa);
        cena.setFill(Color.TRANSPARENT);
        janela.setScene(cena);

        janela.widthProperty().addListener((o, antes, agora) -> {
            if (Math.abs(LARGURA.get() - agora.doubleValue()) > 0.5) {
                LARGURA.set(Math.round(agora.doubleValue()));
            }
        });
        janela.heightProperty().addListener((o, antes, agora) -> {
            if (Math.abs(ALTURA.get() - agora.doubleValue()) > 0.5) {
                ALTURA.set(Math.round(agora.doubleValue()));
            }
        });

        ligarMouse(cena);

        cena.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                desativar();
            }
        });

        MenuItem fechar = new MenuItem("Desligar a régua");
        fechar.setOnAction(e -> desativar());
        ContextMenu menu = new ContextMenu(fechar);
        faixa.setOnContextMenuRequested(e -> menu.show(faixa, e.getScreenX(), e.getScreenY()));

        // Fechar pelo Alt+F4 ou pelo sistema também precisa desligar o estado.
        janela.setOnHidden(e -> ATIVA.set(false));
    }

    /** Centraliza na primeira vez, ou volta onde estava (se ainda houver tela ali). */
    private static void posicionar() {
        Config config = Config.get();
        double largura = LARGURA.get();
        double altura = ALTURA.get();
        if (config.reguaPosicionada
                && !Screen.getScreensForRectangle(config.reguaX, config.reguaY, largura, altura).isEmpty()) {
            janela.setX(config.reguaX);
            janela.setY(config.reguaY);
            return;
        }
        Rectangle2D tela = Screen.getPrimary().getVisualBounds();
        janela.setX(tela.getMinX() + (tela.getWidth() - largura) / 2);
        janela.setY(tela.getMinY() + (tela.getHeight() - altura) / 2);
    }

    /** A janela acompanha as propriedades, quando é o painel que as muda. */
    private static void acompanharTamanho() {
        if (janela == null || !janela.isShowing()) {
            return;
        }
        if (Math.abs(janela.getWidth() - LARGURA.get()) > 0.5) {
            janela.setWidth(LARGURA.get());
        }
        if (Math.abs(janela.getHeight() - ALTURA.get()) > 0.5) {
            janela.setHeight(ALTURA.get());
        }
    }

    private static void pintar() {
        if (faixa == null) {
            return;
        }
        Cor cor = COR.get();
        double opacidade = limitar(OPACIDADE.get(), OPACIDADE_MIN, OPACIDADE_MAX);
        CornerRadii cantos = new CornerRadii(3);
        faixa.setBackground(new Background(new BackgroundFill(
                Color.rgb(cor.r, cor.g, cor.b, opacidade), cantos, Insets.EMPTY)));
        // A borda não some com a opacidade: com o preenchimento quase
        // transparente, é ela que mostra onde a faixa termina.
        faixa.setBorder(new Border(new BorderStroke(
                Color.rgb(cor.r, cor.g, cor.b, 0.95), BorderStrokeStyle.SOLID, cantos, new BorderWidths(1.5))));
    }

    // ------------------------------------------------------------ mouse

    private static final class Estado {
        double mouseX;
        double mouseY;
        double x;
        double y;
        double largura;
        double altura;
        boolean esquerda;
        boolean direita;
        boolean cima;
        boolean baixo;

        boolean naBorda() {
            return esquerda || direita || cima || baixo;
        }
    }

    private static void ligarMouse(Scene cena) {
        Estado estado = new Estado();

        cena.setOnMouseMoved(e -> {
            detectar(e, cena, estado);
            cena.setCursor(cursor(estado));
        });

        cena.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            detectar(e, cena, estado);
            estado.mouseX = e.getScreenX();
            estado.mouseY = e.getScreenY();
            estado.x = janela.getX();
            estado.y = janela.getY();
            estado.largura = janela.getWidth();
            estado.altura = janela.getHeight();
        });

        cena.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            double dx = e.getScreenX() - estado.mouseX;
            double dy = e.getScreenY() - estado.mouseY;

            if (!estado.naBorda()) {
                janela.setX(estado.x + dx);
                janela.setY(estado.y + dy);
                return;
            }
            if (estado.direita) {
                janela.setWidth(Math.max(LARGURA_MIN, estado.largura + dx));
            }
            if (estado.baixo) {
                janela.setHeight(limitar(estado.altura + dy, ALTURA_MIN, ALTURA_MAX));
            }
            if (estado.esquerda) {
                double nova = Math.max(LARGURA_MIN, estado.largura - dx);
                janela.setX(estado.x + (estado.largura - nova));
                janela.setWidth(nova);
            }
            if (estado.cima) {
                double nova = limitar(estado.altura - dy, ALTURA_MIN, ALTURA_MAX);
                janela.setY(estado.y + (estado.altura - nova));
                janela.setHeight(nova);
            }
        });

        cena.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                guardar();
            }
        });
    }

    private static void detectar(MouseEvent e, Scene cena, Estado estado) {
        double borda = Math.min(BORDA, Math.min(cena.getWidth(), cena.getHeight()) / 3);
        estado.esquerda = e.getX() < borda;
        estado.direita = e.getX() > cena.getWidth() - borda;
        estado.cima = e.getY() < borda;
        estado.baixo = e.getY() > cena.getHeight() - borda;
    }

    private static Cursor cursor(Estado s) {
        if ((s.esquerda && s.cima) || (s.direita && s.baixo)) {
            return Cursor.NW_RESIZE;
        }
        if ((s.direita && s.cima) || (s.esquerda && s.baixo)) {
            return Cursor.NE_RESIZE;
        }
        if (s.esquerda || s.direita) {
            return Cursor.H_RESIZE;
        }
        if (s.cima || s.baixo) {
            return Cursor.V_RESIZE;
        }
        return Cursor.MOVE;
    }

    static double limitar(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}
