package br.com.myapp.ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * Devolve o redimensionar pelas bordas a uma janela sem decoração.
 *
 * Ao desenhar a própria barra de título, a janela perde a moldura do Windows —
 * e com ela o arrastar das bordas. Esta classe observa o mouse perto dos
 * cantos, troca o cursor e redimensiona conforme o arrasto.
 *
 * A faixa sensível tem 6 pixels: larga o bastante para acertar sem mirar, e
 * estreita o bastante para não atrapalhar quem quer clicar no que está perto
 * da borda.
 */
public final class RedimensionadorJanela {

    private static final int FAIXA = 6;

    private RedimensionadorJanela() {
    }

    /** Liga o redimensionamento nesta janela. */
    public static void instalar(Stage janela, Scene cena, double larguraMinima, double alturaMinima) {
        Estado estado = new Estado();

        cena.setOnMouseMoved(evento -> {
            Borda borda = detectar(evento, cena);
            cena.setCursor(borda.cursor);
            estado.borda = borda;
        });

        cena.setOnMousePressed(evento -> {
            estado.inicioX = evento.getScreenX();
            estado.inicioY = evento.getScreenY();
            estado.larguraInicial = janela.getWidth();
            estado.alturaInicial = janela.getHeight();
            estado.janelaX = janela.getX();
            estado.janelaY = janela.getY();
        });

        cena.setOnMouseDragged(evento -> {
            if (estado.borda == Borda.NENHUMA) {
                return;
            }
            double deltaX = evento.getScreenX() - estado.inicioX;
            double deltaY = evento.getScreenY() - estado.inicioY;

            if (estado.borda.direita) {
                janela.setWidth(Math.max(larguraMinima, estado.larguraInicial + deltaX));
            }
            if (estado.borda.baixo) {
                janela.setHeight(Math.max(alturaMinima, estado.alturaInicial + deltaY));
            }
            if (estado.borda.esquerda) {
                double novaLargura = Math.max(larguraMinima, estado.larguraInicial - deltaX);
                // Só move a janela enquanto ela ainda está encolhendo: sem
                // isto, ao atingir a largura mínima a janela continuaria
                // deslizando para a direita.
                if (novaLargura > larguraMinima) {
                    janela.setX(estado.janelaX + deltaX);
                }
                janela.setWidth(novaLargura);
            }
            if (estado.borda.cima) {
                double novaAltura = Math.max(alturaMinima, estado.alturaInicial - deltaY);
                if (novaAltura > alturaMinima) {
                    janela.setY(estado.janelaY + deltaY);
                }
                janela.setHeight(novaAltura);
            }
        });

        cena.setOnMouseReleased(evento -> cena.setCursor(Cursor.DEFAULT));
    }

    private static Borda detectar(MouseEvent evento, Scene cena) {
        double x = evento.getSceneX();
        double y = evento.getSceneY();
        double largura = cena.getWidth();
        double altura = cena.getHeight();

        boolean esquerda = x < FAIXA;
        boolean direita = x > largura - FAIXA;
        boolean cima = y < FAIXA;
        boolean baixo = y > altura - FAIXA;

        if (cima && esquerda) {
            return Borda.NOROESTE;
        }
        if (cima && direita) {
            return Borda.NORDESTE;
        }
        if (baixo && esquerda) {
            return Borda.SUDOESTE;
        }
        if (baixo && direita) {
            return Borda.SUDESTE;
        }
        if (esquerda) {
            return Borda.OESTE;
        }
        if (direita) {
            return Borda.LESTE;
        }
        if (cima) {
            return Borda.NORTE;
        }
        if (baixo) {
            return Borda.SUL;
        }
        return Borda.NENHUMA;
    }

    /** Qual borda o cursor encostou, e para onde ela cresce. */
    private enum Borda {
        NENHUMA(Cursor.DEFAULT, false, false, false, false),
        NORTE(Cursor.N_RESIZE, true, false, false, false),
        SUL(Cursor.S_RESIZE, false, true, false, false),
        LESTE(Cursor.E_RESIZE, false, false, false, true),
        OESTE(Cursor.W_RESIZE, false, false, true, false),
        NORDESTE(Cursor.NE_RESIZE, true, false, false, true),
        NOROESTE(Cursor.NW_RESIZE, true, false, true, false),
        SUDESTE(Cursor.SE_RESIZE, false, true, false, true),
        SUDOESTE(Cursor.SW_RESIZE, false, true, true, false);

        final Cursor cursor;
        final boolean cima;
        final boolean baixo;
        final boolean esquerda;
        final boolean direita;

        Borda(Cursor cursor, boolean cima, boolean baixo, boolean esquerda, boolean direita) {
            this.cursor = cursor;
            this.cima = cima;
            this.baixo = baixo;
            this.esquerda = esquerda;
            this.direita = direita;
        }
    }

    /** Guarda o ponto de partida do arrasto. */
    private static class Estado {
        Borda borda = Borda.NENHUMA;
        double inicioX;
        double inicioY;
        double larguraInicial;
        double alturaInicial;
        double janelaX;
        double janelaY;
    }
}
