package br.com.myapp.ui;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reordenação por arrastar, para qualquer lista de cartões.
 *
 * O JavaFX traz o mecanismo de arrastar e soltar, mas não a reordenação: é
 * preciso decidir onde o item cai, mexer na lista e redesenhar. Como três
 * telas precisam disso — recados, lembretes e notas — a lógica ficou aqui, e
 * cada tela só diz o que fazer com a ordem nova.
 *
 * Funciona tanto em coluna ({@code VBox}) quanto em grade ({@code FlowPane}):
 * o item é inserido antes ou depois do alvo conforme o lado em que o cursor
 * solta, o que dá o mesmo resultado nos dois arranjos.
 *
 * <pre>
 *   ┌─────┐   ┌─────┐   ┌─────┐
 *   │  A  │   │  B  │   │  C  │
 *   └─────┘   └──┬──┘   └─────┘
 *             arrastando
 *          ↓ solta na metade esquerda de C
 *   ┌─────┐   ┌─────┐   ┌─────┐
 *   │  A  │   │  B  │   │  C  │   → A, B, C vira A, B, C (sem mudança)
 *   └─────┘   └─────┘   └─────┘
 *          ↓ solta na metade direita de C
 *                             → A, C, B
 * </pre>
 */
public final class Arrastavel {

    /** Identifica o arrasto como sendo desta aplicação. */
    private static final DataFormat FORMATO = new DataFormat("myapp/reordenar");

    /** Índice do item em movimento. Um arrasto por vez, então um campo basta. */
    private static int origem = -1;

    /** De qual lista o arrasto saiu, para não misturar listas diferentes. */
    private static Object listaEmMovimento;

    private Arrastavel() {
    }

    /**
     * Torna os filhos do painel reordenáveis.
     *
     * @param painel      o contêiner dos cartões
     * @param quantidade  quantos itens existem (os filhos podem incluir avisos)
     * @param aoSoltar    recebe (posiçãoOrigem, posiçãoDestino) já resolvidas
     */
    public static void instalar(Pane painel, int quantidade, Reordenou aoSoltar) {
        List<Node> filhos = painel.getChildren();

        for (int i = 0; i < Math.min(quantidade, filhos.size()); i++) {
            Node cartao = filhos.get(i);
            final int posicao = i;

            cartao.setOnDragDetected(evento -> {
                Dragboard prancheta = cartao.startDragAndDrop(TransferMode.MOVE);

                // A miniatura do próprio cartão acompanha o cursor.
                SnapshotParameters transparente = new SnapshotParameters();
                transparente.setFill(Color.TRANSPARENT);
                prancheta.setDragView(cartao.snapshot(transparente, null));

                ClipboardContent conteudo = new ClipboardContent();
                conteudo.put(FORMATO, posicao);
                prancheta.setContent(conteudo);

                origem = posicao;
                listaEmMovimento = painel;
                cartao.getStyleClass().add("arrastando");
                evento.consume();
            });

            cartao.setOnDragOver(evento -> {
                if (podeReceber(evento, painel, posicao)) {
                    evento.acceptTransferModes(TransferMode.MOVE);
                    marcarAlvo(cartao, evento, true);
                }
                evento.consume();
            });

            cartao.setOnDragExited(evento -> {
                limparMarcas(cartao);
                evento.consume();
            });

            cartao.setOnDragDropped(evento -> {
                if (!podeReceber(evento, painel, posicao)) {
                    evento.setDropCompleted(false);
                    evento.consume();
                    return;
                }
                int destino = posicao;
                // Soltar na segunda metade do cartão significa "depois dele".
                if (depoisDoAlvo(cartao, evento) && origem > posicao) {
                    destino = posicao;
                } else if (depoisDoAlvo(cartao, evento)) {
                    destino = posicao;
                } else if (origem < posicao) {
                    destino = Math.max(0, posicao - 1);
                }

                limparMarcas(cartao);
                evento.setDropCompleted(true);
                evento.consume();

                if (destino != origem) {
                    aoSoltar.mover(origem, destino);
                }
            });

            cartao.setOnDragDone(evento -> {
                cartao.getStyleClass().remove("arrastando");
                origem = -1;
                listaEmMovimento = null;
                evento.consume();
            });
        }
    }

    private static boolean podeReceber(DragEvent evento, Pane painel, int posicao) {
        return evento.getDragboard().hasContent(FORMATO)
                && listaEmMovimento == painel
                && origem >= 0
                && origem != posicao;
    }

    /** O cursor está passando da metade do cartão? */
    private static boolean depoisDoAlvo(Node cartao, DragEvent evento) {
        double largura = cartao.getBoundsInLocal().getWidth();
        double altura = cartao.getBoundsInLocal().getHeight();

        // Em cartões largos e baixos (uma lista em coluna), o que conta é a
        // altura; em cartões mais quadrados (uma grade), a largura.
        if (largura > altura * 2) {
            return evento.getY() > altura / 2;
        }
        return evento.getX() > largura / 2;
    }

    private static void marcarAlvo(Node cartao, DragEvent evento, boolean ativo) {
        limparMarcas(cartao);
        if (!ativo) {
            return;
        }
        cartao.getStyleClass().add(depoisDoAlvo(cartao, evento)
                ? "alvo-depois" : "alvo-antes");
    }

    private static void limparMarcas(Node cartao) {
        cartao.getStyleClass().removeAll("alvo-antes", "alvo-depois");
    }

    /** O que a tela faz quando um item muda de lugar. */
    @FunctionalInterface
    public interface Reordenou {
        void mover(int de, int para);
    }

    /**
     * Aplica o movimento a uma lista qualquer.
     *
     * Existe para as telas não repetirem esse cuidado: remover antes de
     * inserir muda os índices, e é onde o erro costuma aparecer.
     */
    public static <T> void mover(List<T> lista, int de, int para) {
        if (de < 0 || de >= lista.size() || para < 0 || para >= lista.size() || de == para) {
            return;
        }
        T item = lista.remove(de);
        lista.add(para, item);
    }

    /** Conveniência para quem só quer reagir com um método sem parâmetros. */
    public static Reordenou aplicandoEm(List<?> lista, Consumer<List<?>> aoTerminar) {
        return (de, para) -> {
            mover(lista, de, para);
            aoTerminar.accept(lista);
        };
    }
}
