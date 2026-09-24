package br.com.myapp.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.util.Duration;

/**
 * Os botões do aplicativo, montados sempre do mesmo jeito.
 *
 * <p>Antes cada tela montava os seus: uma escrevia {@code "+  Novo"}, outra
 * {@code "➕ Novo"}, uma punha a classe de estilo, outra esquecia, e a dica
 * de cada botão tinha um atraso diferente. O resultado é uma interface que
 * parece feita por quatro pessoas que não se falaram.
 *
 * <p>Passando por aqui, o botão nasce certo:
 *
 * <pre>
 *   Botoes.primario("Novo lembrete", Icone.Simbolo.ADICIONAR)
 *   Botoes.comum("Ouvir", Icone.Simbolo.SOM)
 *   Botoes.perigo("Excluir categoria", Icone.Simbolo.EXCLUIR)
 *   Botoes.icone(Icone.Simbolo.COPIAR, "Copiar a senha")
 *   Botoes.link("Ver senhas anteriores")
 * </pre>
 *
 * <h2>Qual usar</h2>
 *
 * <table border="1">
 *   <caption>Hierarquia dos botões</caption>
 *   <tr><th>Tipo</th><th>Quando</th></tr>
 *   <tr><td>primário</td><td>A ação principal da tela. <b>Um por tela.</b></td></tr>
 *   <tr><td>comum</td><td>Todo o resto</td></tr>
 *   <tr><td>perigo</td><td>Destrói algo; vazado até o momento de confirmar</td></tr>
 *   <tr><td>ícone</td><td>Ação repetida em lista, onde o texto não caberia</td></tr>
 *   <tr><td>link</td><td>Caminho secundário dentro de um bloco de texto</td></tr>
 * </table>
 */
public final class Botoes {

    /** Tamanho do ícone dentro de um botão com texto. */
    private static final double ICONE_COM_TEXTO = 16;

    /** Tamanho do ícone num botão que só tem ícone. */
    private static final double ICONE_SOZINHO = 18;

    /** Quanto o mouse precisa ficar parado antes de a dica aparecer. */
    private static final Duration ESPERA_DA_DICA = Duration.millis(150);

    /** Distância entre a dica e o nó ao qual ela está presa. */
    private static final double FOLGA_DA_DICA = 6;

    private Botoes() {
    }

    // ------------------------------------------------------- com texto

    /** A ação principal da tela. Deve haver só uma. */
    public static Button primario(String texto, Icone.Simbolo simbolo) {
        return montar(texto, simbolo, "botao-primario");
    }

    public static Button primario(String texto) {
        return montar(texto, null, "botao-primario");
    }

    /** O botão de todo dia. */
    public static Button comum(String texto, Icone.Simbolo simbolo) {
        return montar(texto, simbolo, "botao");
    }

    public static Button comum(String texto) {
        return montar(texto, null, "botao");
    }

    /** Ação destrutiva: vermelho vazado, para avisar sem gritar. */
    public static Button perigo(String texto, Icone.Simbolo simbolo) {
        return montar(texto, simbolo, "botao-perigo");
    }

    /**
     * Botão baixo, para o cabeçalho de uma seção.
     *
     * <p>Um botão comum tem 40 px de altura e empurraria a linha do título
     * para baixo; este cabe na altura do rótulo e só ganha fundo com o mouse
     * em cima. Serve para ações que pertencem a um bloco, e não à tela
     * inteira — como copiar o código que está logo abaixo dele.
     */
    public static Button miudo(String texto, Icone.Simbolo simbolo) {
        Button b = new Button(texto);
        b.getStyleClass().add("botao-miudo");
        if (simbolo != null) {
            b.setGraphic(Icone.de(simbolo, 13));
        }
        return b;
    }

    /** Parece um link: caminho secundário dentro de um bloco de texto. */
    public static Button link(String texto) {
        return montar(texto, null, "botao-link");
    }

    // ------------------------------------------------------- só ícone

    /**
     * Botão quadrado com ícone, para as ações que se repetem em cada linha de
     * uma lista.
     *
     * <p>A dica é obrigatória: um botão sem texto que também não explica o que
     * faz obriga o usuário a descobrir clicando — e algumas destas ações
     * excluem coisas.
     */
    public static Button icone(Icone.Simbolo simbolo, String dica) {
        Button b = new Button();
        b.getStyleClass().add("botao-icone");
        b.setGraphic(Icone.de(simbolo, ICONE_SOZINHO));
        instalarDica(b, dica);
        return b;
    }

    /** O mesmo, em vermelho ao passar o mouse: exclui. */
    public static Button iconePerigo(Icone.Simbolo simbolo, String dica) {
        Button b = icone(simbolo, dica);
        b.getStyleClass().add("botao-icone-perigo");
        return b;
    }

    /** Troca o desenho de um botão de ícone já montado. */
    public static void trocarIcone(Button botao, Icone.Simbolo simbolo) {
        botao.setGraphic(Icone.de(simbolo, ICONE_SOZINHO));
    }

    // ----------------------------------------------------------- dicas

    /**
     * A dica que aparece ao parar o mouse sobre o botão.
     *
     * <p>O padrão do JavaFX espera cerca de um segundo antes de mostrar e
     * some depressa. Na prática o usuário desiste antes de descobrir para que
     * serve o botão. Aqui aparece quase de imediato e fica tempo de ler.
     *
     * <p>Prefira {@link #instalarDica}: só ele resolve o problema de posição
     * descrito lá.
     */
    public static Tooltip dica(String texto) {
        Tooltip t = new Tooltip(texto);
        t.setShowDelay(Duration.millis(150));
        t.setShowDuration(Duration.seconds(20));
        t.setHideDelay(Duration.millis(100));
        t.setWrapText(true);
        t.setMaxWidth(280);
        return t;
    }

    /**
     * Prende uma dica a um nó, ancorada <b>ao nó</b> e não ao cursor.
     *
     * <p>O JavaFX abre a dica ao lado do ponteiro e, quando ela não cabe na
     * tela, empurra a janelinha para dentro — o que, perto da borda direita,
     * a traz para debaixo do próprio cursor. O cursor então sai do botão (a
     * dica está na frente), a dica se esconde, o cursor volta a estar sobre o
     * botão, a dica reabre: o ponteiro fica alternando entre mãozinha e seta
     * várias vezes por segundo, com o mouse parado.
     *
     * <p>Ancorando embaixo do nó — ou em cima, quando não há espaço embaixo —
     * a dica nunca cai sob o ponteiro, e o piscar acaba. De quebra a posição
     * passa a ser previsível, sempre no mesmo lugar em relação ao botão.
     */
    public static Tooltip instalarDica(Node no, String texto) {
        Tooltip balao = dica(texto);

        PauseTransition atraso = new PauseTransition(ESPERA_DA_DICA);
        atraso.setOnFinished(e -> mostrarPresaAoNo(no, balao));

        no.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> atraso.playFromStart());
        no.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            atraso.stop();
            balao.hide();
        });
        // Clicar já respondeu a pergunta que a dica responderia.
        no.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            atraso.stop();
            balao.hide();
        });

        // Devolvida para quem precisa trocar o texto depois — um botão que
        // alterna entre dois estados, por exemplo. Instalar de novo a cada
        // troca empilharia tratadores e abriria dicas sobrepostas.
        return balao;
    }

    private static void mostrarPresaAoNo(Node no, Tooltip balao) {
        if (no.getScene() == null || no.getScene().getWindow() == null || !no.isVisible()) {
            return;
        }
        Bounds naTela = no.localToScreen(no.getBoundsInLocal());
        if (naTela == null) {
            return;
        }

        // O encaixe automático do JavaFX é justamente o que empurrava a dica
        // para cima do cursor. Aqui a posição é decidida à mão, então ele
        // precisa sair do caminho.
        balao.setAutoFix(false);

        // Mostra uma vez para a janelinha ganhar tamanho: antes de aparecer,
        // a largura e a altura dela ainda são zero.
        balao.show(no, naTela.getMinX(), naTela.getMaxY() + FOLGA_DA_DICA);

        Rectangle2D tela = Screen.getScreensForRectangle(
                        naTela.getMinX(), naTela.getMinY(), 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary())
                .getVisualBounds();

        // Encostada à esquerda do nó, mas sem sair da tela pela direita.
        double x = Math.min(naTela.getMinX(), tela.getMaxX() - balao.getWidth() - FOLGA_DA_DICA);
        balao.setX(Math.max(tela.getMinX() + FOLGA_DA_DICA, x));

        // Embaixo do nó; se não couber, em cima dele. Nunca sobre ele.
        double abaixo = naTela.getMaxY() + FOLGA_DA_DICA;
        balao.setY(abaixo + balao.getHeight() > tela.getMaxY()
                ? naTela.getMinY() - FOLGA_DA_DICA - balao.getHeight()
                : abaixo);
    }

    // ---------------------------------------------------------- interno

    private static Button montar(String texto, Icone.Simbolo simbolo, String estilo) {
        Button b = new Button(texto);
        b.getStyleClass().add(estilo);
        if (simbolo != null) {
            b.setGraphic(Icone.de(simbolo, ICONE_COM_TEXTO));
        }
        return b;
    }
}
