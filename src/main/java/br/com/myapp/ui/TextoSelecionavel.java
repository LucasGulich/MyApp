package br.com.myapp.ui;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import javafx.scene.text.HitInfo;
import javafx.scene.text.TextFlow;

/**
 * Texto colorido que se deixa selecionar com o mouse.
 *
 * <p>O destaque de sintaxe é um {@link TextFlow}: um pedaço de texto para cada
 * cor. É o único jeito de o JavaFX pintar trechos diferentes de cores
 * diferentes, mas o {@code TextFlow} é só desenho — não tem cursor, não tem
 * seleção, não responde ao Ctrl+C. Para copiar meia consulta SQL era preciso
 * copiar tudo e apagar o resto em outro lugar.
 *
 * <p>Trocar por um {@code TextArea} daria a seleção e levaria as cores. Esta
 * classe faz o contrário: mantém o desenho e acrescenta a seleção por cima,
 * com as duas ferramentas que o próprio {@code TextFlow} oferece —
 * {@code hitTest}, que diz em qual caractere o mouse está, e
 * {@code rangeShape}, que devolve o contorno de um trecho para pintar o fundo.
 *
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │ SELECT nome ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓    │  ← camada de seleção, atrás
 *   │ ▓▓▓▓▓▓▓▓ WHERE ativo = 1         │  ← texto colorido, na frente
 *   └──────────────────────────────────┘
 * </pre>
 *
 * <p>Comporta-se como um campo só de leitura: arrastar seleciona, dois
 * cliques pegam a palavra, três a linha, Shift+clique estende, Ctrl+A pega
 * tudo, Ctrl+C copia e o botão direito abre o menu com as duas coisas.
 */
public class TextoSelecionavel extends StackPane {

    private final TextFlow fluxo;
    private final String texto;
    private final Path selecao = new Path();
    private final ContextMenu menu = new ContextMenu();

    /** Onde a seleção começou e onde está a outra ponta, em caracteres. */
    private int ancora;
    private int ponta;

    /**
     * @param fluxo o texto já montado
     * @param texto o mesmo texto, por extenso — é dele que sai o que se copia
     */
    public TextoSelecionavel(TextFlow fluxo, String texto) {
        this.fluxo = fluxo;
        this.texto = texto == null ? "" : texto;

        getStyleClass().add("texto-selecionavel");
        setFocusTraversable(true);
        setCursor(Cursor.TEXT);
        setAlignment(Pos.TOP_LEFT);

        // Fora do cálculo de tamanho: o contorno segue o texto, não o contrário.
        selecao.getStyleClass().add("texto-selecionavel-fundo");
        selecao.setManaged(false);
        selecao.setMouseTransparent(true);

        getChildren().addAll(selecao, fluxo);

        montarMenu();
        instalarMouse();
        instalarTeclado();
    }

    // ------------------------------------------------------------- mouse

    private void instalarMouse() {
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            requestFocus();
            int indice = indiceSob(e);
            switch (e.getClickCount()) {
                case 1 -> {
                    if (!e.isShiftDown()) {
                        ancora = indice;
                    }
                    ponta = indice;
                }
                case 2 -> selecionarPalavra(indice);
                default -> selecionarLinha(indice);
            }
            redesenhar();
        });

        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.isPrimaryButtonDown()) {
                ponta = indiceSob(e);
                redesenhar();
            }
        });

        setOnContextMenuRequested(e -> {
            menu.getItems().get(0).setDisable(!temSelecao());
            menu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /** O caractere sob o mouse, contado desde o começo do texto. */
    private int indiceSob(MouseEvent e) {
        Point2D ponto = fluxo.sceneToLocal(e.getSceneX(), e.getSceneY());
        HitInfo acerto = fluxo.hitTest(ponto);
        return Math.max(0, Math.min(texto.length(), acerto.getInsertionIndex()));
    }

    private void selecionarPalavra(int indice) {
        int inicio = indice;
        int fim = indice;
        while (inicio > 0 && fazParteDaPalavra(texto.charAt(inicio - 1))) {
            inicio--;
        }
        while (fim < texto.length() && fazParteDaPalavra(texto.charAt(fim))) {
            fim++;
        }
        ancora = inicio;
        ponta = fim;
    }

    /** Letra, número ou sublinhado: o que forma um nome de coluna ou variável. */
    private static boolean fazParteDaPalavra(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void selecionarLinha(int indice) {
        // Sem quebra antes, lastIndexOf devolve -1 e a linha começa no zero.
        int inicio = texto.lastIndexOf('\n', indice - 1) + 1;
        int fim = texto.indexOf('\n', indice);
        ancora = inicio;
        ponta = fim < 0 ? texto.length() : fim;
    }

    // ----------------------------------------------------------- teclado

    private void instalarTeclado() {
        setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
                copiar();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.A) {
                selecionarTudo();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE && temSelecao()) {
                ancora = ponta;
                redesenhar();
                e.consume();
            }
        });
    }

    private void montarMenu() {
        MenuItem copiar = new MenuItem("Copiar");
        copiar.setOnAction(e -> copiar());

        MenuItem tudo = new MenuItem("Selecionar tudo");
        tudo.setOnAction(e -> selecionarTudo());

        menu.getItems().addAll(copiar, tudo);
    }

    // ------------------------------------------------------------- ações

    public boolean temSelecao() {
        return ancora != ponta;
    }

    /** O trecho selecionado, ou vazio. */
    public String selecionado() {
        return texto.substring(Math.min(ancora, ponta), Math.max(ancora, ponta));
    }

    public void selecionarTudo() {
        ancora = 0;
        ponta = texto.length();
        redesenhar();
    }

    private void copiar() {
        if (!temSelecao()) {
            return;
        }
        AreaTransferencia.copiar(selecionado());
        Aviso.info("Trecho copiado.");
    }

    // ------------------------------------------------------------ desenho

    /**
     * Pinta o fundo da seleção.
     *
     * <p>O contorno vem nas coordenadas do {@code TextFlow}, e a camada de
     * seleção é irmã dele: ela copia o deslocamento do texto para as duas
     * coincidirem, inclusive o recuo do padding.
     */
    private void redesenhar() {
        if (!temSelecao()) {
            selecao.getElements().clear();
            return;
        }
        selecao.getElements().setAll(
                fluxo.rangeShape(Math.min(ancora, ponta), Math.max(ancora, ponta)));
        selecao.setLayoutX(fluxo.getLayoutX());
        selecao.setLayoutY(fluxo.getLayoutY());
    }

    /** A largura mudou, as linhas quebraram em outro ponto: redesenha. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        redesenhar();
    }
}
