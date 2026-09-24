package br.com.myapp.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os ícones do aplicativo, desenhados em vetor.
 *
 * <p>Antes cada botão trazia um emoji. Emoji é desenho de fonte: em 14 ou 16
 * pixels vira um borrão colorido em que não se distingue um lápis de uma
 * chave, a cor não acompanha o tema (o emoji é sempre colorido, mesmo em um
 * botão azul) e o desenho muda de uma máquina para outra conforme a fonte
 * instalada. Um ícone de interface precisa ser legível pequeno, monocromático
 * e igual em todo lugar — que é exatamente o que um emoji não é.
 *
 * <p>Aqui cada símbolo é um traçado vetorial de contorno, desenhado numa
 * grade de 24×24 com espessura 2 e pontas arredondadas — o mesmo traço em
 * todos, que é o que faz um conjunto de ícones parecer um conjunto. Como é
 * vetor, cresce sem serrilhar; como é traçado e não preenchido, a cor vem do
 * CSS e acompanha o tema, o estado do botão e o item ativo do menu.
 *
 * <h2>Como usar</h2>
 *
 * <pre>
 *   botao.setGraphic(Icone.de(Simbolo.EDITAR));              // 18 px
 *   botao.setGraphic(Icone.de(Simbolo.EXCLUIR, 20));         // tamanho fixo
 *   rotulo.setGraphic(Icone.de(Simbolo.CADEADO, 14, "icone-atencao"));
 * </pre>
 *
 * <h2>Como colorir</h2>
 *
 * O nó devolvido tem a classe {@code icone} e o traçado tem {@code
 * icone-forma}. A cor padrão está no CSS; para mudar em um lugar específico
 * existem os modificadores {@code icone-primario}, {@code icone-perigo},
 * {@code icone-sucesso}, {@code icone-atencao}, {@code icone-fraco} e {@code
 * icone-claro}, ou uma regra de contexto do tipo
 * {@code .item-menu.ativo .icone-forma}.
 *
 * @see <a href="https://lucide.dev">O traçado segue a convenção do Lucide</a>
 */
public final class Icone {

    /** Tamanho usado quando nada é dito: cabe ao lado de um texto de 13 px. */
    public static final double PADRAO = 18;

    private Icone() {
    }

    /**
     * Cada símbolo disponível.
     *
     * <p>Os nomes descrevem o <b>papel</b> e não o desenho — {@code EXCLUIR} e
     * não {@code LIXEIRA} — para que trocar o desenho um dia não obrigue a
     * mexer em quem usa.
     */
    public enum Simbolo {

        // ---------------------------------------------------------- módulos
        INICIO("M3 9.5 12 3l9 6.5V20a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z"
                + "M9 22v-9h6v9"),
        LEMBRETE("M18 8A6 6 0 0 0 6 8c0 7 -3 9 -3 9h18s-3 -2 -3 -9"
                + "M13.73 21a2 2 0 0 1 -3.46 0"),
        NOTA("M14 2H6a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2V8z"
                + "M14 2v6h6M16 13H8M16 17H8M10 9H8"),
        QUADRO("M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1V4a1 1 0 0 1 1 -1z"
                + "M9 3v18M15 3v18"),
        CONFIGURACOES("M12.2 2h-.4a2 2 0 0 0 -2 2v.2a2 2 0 0 1 -1 1.7l-.4.3a2 2 0 0 1 -2 0"
                + "l-.2-.1a2 2 0 0 0 -2.7.7l-.2.4a2 2 0 0 0 .7 2.7l.2.1a2 2 0 0 1 1 1.7v.5"
                + "a2 2 0 0 1 -1 1.7l-.2.1a2 2 0 0 0 -.7 2.7l.2.4a2 2 0 0 0 2.7.7l.2-.1"
                + "a2 2 0 0 1 2 0l.4.3a2 2 0 0 1 1 1.7v.2a2 2 0 0 0 2 2h.4a2 2 0 0 0 2 -2v-.2"
                + "a2 2 0 0 1 1 -1.7l.4-.3a2 2 0 0 1 2 0l.2.1a2 2 0 0 0 2.7-.7l.2-.4"
                + "a2 2 0 0 0 -.7-2.7l-.2-.1a2 2 0 0 1 -1 -1.7v-.5a2 2 0 0 1 1 -1.7l.2-.1"
                + "a2 2 0 0 0 .7-2.7l-.2-.4a2 2 0 0 0 -2.7-.7l-.2.1a2 2 0 0 1 -2 0l-.4-.3"
                + "a2 2 0 0 1 -1 -1.7V4a2 2 0 0 0 -2 -2z"
                + "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z"),
        MARCA("M13 2 3 14h9l-1 8 10-12h-9z"),

        // ------------------------------------------------------- segurança
        CADEADO("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z"
                + "M7 11V7a5 5 0 0 1 10 0v4"),
        CADEADO_ABERTO("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z"
                + "M7 11V7a5 5 0 0 1 9.9 -1"),
        ESCUDO("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
        CHAVE("M15.5 7.5 21 2l1.5 1.5L21 5l1.5 1.5L20 9l-1.5-1.5"
                + "M15.5 7.5 11.4 11.6"
                + "M11.4 11.6a5.5 5.5 0 1 1 -7.8 7.8 5.5 5.5 0 0 1 7.8 -7.8z"),
        OLHO("M1 12s4-8 11-8 11 8 11 8 -4 8 -11 8 -11 -8 -11 -8z"
                + "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z"),
        OLHO_FECHADO("M17.9 17.9A10 10 0 0 1 12 20C5 20 1 12 1 12a18.5 18.5 0 0 1 5.1 -5.9"
                + "M9.9 4.2A9 9 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1 -2.2 3.2"
                + "M14.1 14.1a3 3 0 1 1 -4.2 -4.2"
                + "M2 2 22 22"),

        // ---------------------------------------------------------- ações
        ADICIONAR("M12 5v14M5 12h14"),
        EDITAR("M17 3a2.8 2.8 0 0 1 4 4L7.5 20.5 2 22l1.5-5.5z"),
        EXCLUIR("M3 6h18"
                + "M8 6V4a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2"
                + "M19 6l-1 14a2 2 0 0 1 -2 2H8a2 2 0 0 1 -2 -2L5 6"
                + "M10 11v6M14 11v6"),
        DUPLICAR("M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H10a2 2 0 0 1 -2 -2V10a2 2 0 0 1 2 -2z"
                + "M4 16a2 2 0 0 1 -2 -2V4a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2"),
        PAUSAR("M7 4v16M17 4v16"),
        RETOMAR("M6 3.5 20 12 6 20.5z"),
        CONFIRMAR("M20 6 9 17l-5-5"),
        FECHAR("M18 6 6 18M6 6l12 12"),
        BUSCAR("M19 11a8 8 0 1 1 -16 0 8 8 0 0 1 16 0z" + "M21 21l-4.3-4.3"),
        COPIAR("M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2h2"
                + "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1H9a1 1 0 0 1 -1 -1V3a1 1 0 0 1 1 -1z"),
        ABRIR_FORA("M18 13v6a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2h6"
                + "M15 3h6v6M10 14 21 3"),
        RESTAURAR("M3 2v6h6" + "M3.5 15a9 9 0 1 0 2.1 -9.4L3 8"),
        GERAR("M23 4v6h-6M1 20v-6h6"
                + "M3.5 9a9 9 0 0 1 14.9 -3.4L23 10"
                + "M20.5 15a9 9 0 0 1 -14.9 3.4L1 14"),
        ARRASTAR("M9 5.5a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z"
                + "M9 12a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z"
                + "M9 18.5a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z"
                + "M18 5.5a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z"
                + "M18 12a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z"
                + "M18 18.5a1.5 1.5 0 1 1 -3 0 1.5 1.5 0 0 1 3 0z", true),

        // ---------------------------------------------------------- estado
        SUCESSO("M22 11.1V12a10 10 0 1 1 -5.9 -9.1" + "M22 4 12 14l-3-3"),
        ERRO("M22 12a10 10 0 1 1 -20 0 10 10 0 0 1 20 0z" + "M15 9l-6 6M9 9l6 6"),
        ATENCAO("M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h16.9a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0 -3.4 0z"
                + "M12 9v4M12 17.2v.1"),
        INFORMACAO("M22 12a10 10 0 1 1 -20 0 10 10 0 0 1 20 0z" + "M12 16v-4M12 8.2v.1"),
        PERGUNTA("M22 12a10 10 0 1 1 -20 0 10 10 0 0 1 20 0z"
                + "M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17.2v.1"),

        // -------------------------------------------------------- lembretes
        RELOGIO("M22 12a10 10 0 1 1 -20 0 10 10 0 0 1 20 0z" + "M12 6.5V12l3.5 2"),
        CALENDARIO("M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z"
                + "M16 2v4M8 2v4M3 10h18"),
        REPETIR("M17 1l4 4-4 4" + "M3 11V9a4 4 0 0 1 4 -4h14"
                + "M7 23l-4-4 4-4" + "M21 13v2a4 4 0 0 1 -4 4H3"),
        SOM("M11 5 6 9H2v6h4l5 4z"
                + "M19.1 4.9a10 10 0 0 1 0 14.2" + "M15.5 8.5a5 5 0 0 1 0 7"),
        SEM_SOM("M11 5 6 9H2v6h4l5 4z" + "M23 9l-6 6M17 9l6 6"),
        ADIAR("M22 12a10 10 0 1 1 -20 0 10 10 0 0 1 20 0z" + "M12 6.5V12l4 2"
                + "M2 12h3"),
        SINO_MUDO("M13.7 21a2 2 0 0 1 -3.4 0"
                + "M18 8a6 6 0 0 0 -9.3 -5" + "M6.3 6.3A6 6 0 0 0 6 8c0 7 -3 9 -3 9h13"
                + "M2 2 22 22"),

        // ------------------------------------------------------------ notas
        TEXTO("M14 2H6a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2V8z"
                + "M14 2v6h6M16 13H8M16 17H8M10 9H8"),
        CREDENCIAL("M15.5 7.5 21 2l1.5 1.5L21 5l1.5 1.5L20 9l-1.5-1.5"
                + "M15.5 7.5 11.4 11.6"
                + "M11.4 11.6a5.5 5.5 0 1 1 -7.8 7.8 5.5 5.5 0 0 1 7.8 -7.8z"),
        ELO("M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0 -7 -7L11.8 5.2"
                + "M14 11a5 5 0 0 0 -7.5 -.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"),
        CODIGO("M16 18l6-6-6-6M8 6l-6 6 6 6"),
        ESTRELA("M12 2.5l3 6.1 6.7 1 -4.8 4.7 1.1 6.7L12 17.8 6 21l1.1-6.7L2.3 9.6l6.7-1z"),
        ESTRELA_CHEIA("M12 2.5l3 6.1 6.7 1 -4.8 4.7 1.1 6.7L12 17.8 6 21l1.1-6.7L2.3 9.6l6.7-1z", true),
        FIXAR("M12 17v5"
                + "M9 10.8a2 2 0 0 1 -1.1 1.8l-1.8.9A2 2 0 0 0 5 15.2V16a1 1 0 0 0 1 1h12"
                + "a1 1 0 0 0 1 -1v-.8a2 2 0 0 0 -1.1 -1.8l-1.8-.9A2 2 0 0 1 15 10.8V7"
                + "a1 1 0 0 1 1 -1 2 2 0 0 0 0 -4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"),
        RECADO("M15.5 3H5a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2 -2V8.5z"
                + "M15 3v6h6"),
        PASTA("M22 19a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2h5l2 3h9a2 2 0 0 1 2 2z"),
        ETIQUETA("M20.6 13.4 12 22l-9-9V3h10z" + "M7.5 7.5v.1"),

        // --------------------------------------------------- categorias
        BANCO("M20 7c0 1.7-3.6 3-8 3S4 8.7 4 7s3.6-3 8-3 8 1.3 8 3z"
                + "M4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7"
                + "M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"),
        SERVIDOR("M4 3h16a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1V4a1 1 0 0 1 1 -1z"
                + "M4 15h16a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z"
                + "M7 6.5v.1M7 18.5v.1"),
        MONITOR("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z"
                + "M8 21h8M12 17v4"),
        EMPRESA("M4 22V4a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v18"
                + "M16 10h2a2 2 0 0 1 2 2v10M2 22h20"
                + "M8 6h.1M12 6h.1M8 10h.1M12 10h.1M8 14h.1M12 14h.1"),
        FERRAMENTA("M14.7 6.3a4 4 0 0 0 5.3 5.3l-9.4 9.4a2.1 2.1 0 0 1 -3 -3z"
                + "M14.7 6.3 18 3l3 3-3.3 3.3"),
        CARRINHO("M9 21a1 1 0 1 1 -2 0 1 1 0 0 1 2 0z"
                + "M20 21a1 1 0 1 1 -2 0 1 1 0 0 1 2 0z"
                + "M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2 -1.6L23 6H6"),
        GRAFICO("M3 3v18h18" + "M7 15l4-5 4 3 5-7"),
        IDEIA("M9 21h6" + "M10 17h4"
                + "M12 2a6 6 0 0 1 4 10.5c-.7.7-1 1.6-1 2.5H9c0-.9-.3-1.8-1-2.5A6 6 0 0 1 12 2z"),
        CAFE("M17 8h1a4 4 0 0 1 0 8h-1"
                + "M3 8h14v9a4 4 0 0 1 -4 4H7a4 4 0 0 1 -4 -4z"
                + "M6 2v3M10 2v3M14 2v3"),

        // ------------------------------------------------- backup e sistema
        BAIXAR("M21 15v4a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-4" + "M7 10l5 5 5-5M12 15V3"),
        ENVIAR("M21 15v4a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-4" + "M17 8l-5-5-5 5M12 3v12"),
        NUVEM("M18 10h-1.3A7 7 0 1 0 4 16.3" + "M12 12v9M8 17l4-4 4 4"),
        SOL("M16 12a4 4 0 1 1 -8 0 4 4 0 0 1 8 0z"
                + "M12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2"
                + "M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4"),
        LUA("M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"),
        JANELA_MINIMIZAR("M5 12h14"),
        JANELA_MAXIMIZAR("M5.5 5.5h13v13h-13z"),
        JANELA_RESTAURAR("M8.5 8.5h10v10h-10z" + "M5.5 15.5v-10h10"),

        // ----------------------------------------------------- navegação
        VOLTAR("M19 12H5M12 19l-7-7 7-7"),
        AVANCAR("M5 12h14M12 5l7 7-7 7"),
        SETA_BAIXO("M6 9l6 6 6-6"),
        SETA_CIMA("M18 15l-6-6-6 6"),
        SETA_DIREITA("M9 18l6-6-6-6"),
        ORDENAR("M3 6h18M6 12h12M10 18h4"),
        PONTO("M16 12a4 4 0 1 1 -8 0 4 4 0 0 1 8 0z", true);

        private final String traco;
        private final boolean preenchido;

        Simbolo(String traco) {
            this(traco, false);
        }

        Simbolo(String traco, boolean preenchido) {
            this.traco = traco;
            this.preenchido = preenchido;
        }
    }

    // ------------------------------------------------------------- fábrica

    /** O ícone no tamanho padrão. */
    public static Node de(Simbolo simbolo) {
        return de(simbolo, PADRAO);
    }

    /** O ícone em um tamanho específico, em pixels. */
    public static Node de(Simbolo simbolo, double tamanho) {
        return de(simbolo, tamanho, new String[0]);
    }

    /**
     * O ícone com classes de estilo extras — normalmente um modificador de
     * cor, como {@code icone-perigo}.
     */
    public static Node de(Simbolo simbolo, double tamanho, String... classes) {
        SVGPath forma = new SVGPath();
        forma.setContent(simbolo.traco);
        forma.getStyleClass().add("icone-forma");
        forma.setStrokeLineCap(StrokeLineCap.ROUND);
        forma.setStrokeLineJoin(StrokeLineJoin.ROUND);

        // Contorno é o padrão; o preenchido existe para os poucos desenhos
        // que só fazem sentido cheios, como um ponto ou a estrela marcada.
        if (simbolo.preenchido) {
            forma.getStyleClass().add("icone-cheio");
        }

        // O desenho é feito numa grade de 24; a escala leva ao tamanho pedido
        // e leva junto a espessura do traço, que é o que mantém a proporção.
        double escala = tamanho / 24.0;
        forma.setScaleX(escala);
        forma.setScaleY(escala);

        // O Group faz os limites de layout acompanharem a escala; sem ele o
        // ícone ocuparia 24 px de espaço por menor que estivesse desenhado.
        StackPane caixa = new StackPane(new Group(forma));
        caixa.getStyleClass().add("icone");
        caixa.getStyleClass().addAll(classes);
        caixa.setMinSize(tamanho, tamanho);
        caixa.setPrefSize(tamanho, tamanho);
        caixa.setMaxSize(tamanho, tamanho);

        // O ícone continua recebendo o mouse: eventos sobem para o botão que o
        // contém, então nada se perde, e em troca um ícone solto pode ter a
        // sua própria dica — é o caso das marcas de "protegida" e "favorita".
        return caixa;
    }

    // ------------------------------------------------- ícones guardados

    /**
     * Os símbolos oferecidos na escolha de ícone de uma categoria, na ordem
     * em que aparecem.
     *
     * <p>A categoria guarda no banco a <b>chave</b> do ícone ({@code "pasta"},
     * {@code "banco"}…) e não o desenho. Guardar o desenho prenderia o dado a
     * esta versão do programa: trocar o traçado obrigaria a reescrever o
     * banco.
     */
    public static final Map<String, Simbolo> ESCOLHAS = escolhas();

    private static Map<String, Simbolo> escolhas() {
        Map<String, Simbolo> mapa = new LinkedHashMap<>();
        mapa.put("pasta", Simbolo.PASTA);
        mapa.put("nota", Simbolo.TEXTO);
        mapa.put("codigo", Simbolo.CODIGO);
        mapa.put("banco", Simbolo.BANCO);
        mapa.put("servidor", Simbolo.SERVIDOR);
        mapa.put("monitor", Simbolo.MONITOR);
        mapa.put("chave", Simbolo.CHAVE);
        mapa.put("escudo", Simbolo.ESCUDO);
        mapa.put("elo", Simbolo.ELO);
        mapa.put("empresa", Simbolo.EMPRESA);
        mapa.put("ferramenta", Simbolo.FERRAMENTA);
        mapa.put("carrinho", Simbolo.CARRINHO);
        mapa.put("grafico", Simbolo.GRAFICO);
        mapa.put("ideia", Simbolo.IDEIA);
        mapa.put("cafe", Simbolo.CAFE);
        mapa.put("estrela", Simbolo.ESTRELA);
        mapa.put("etiqueta", Simbolo.ETIQUETA);
        mapa.put("calendario", Simbolo.CALENDARIO);
        return mapa;
    }

    /**
     * Traduz o que está gravado na categoria para um símbolo.
     *
     * <p>Aceita tanto as chaves novas quanto os emojis que as categorias
     * criadas antes desta versão guardaram — assim o banco existente continua
     * valendo, sem migração e sem categoria órfã. Qualquer coisa que não seja
     * reconhecida vira uma pasta, que é o padrão de sempre.
     */
    public static Simbolo porChave(String chave) {
        if (chave == null || chave.isBlank()) {
            return Simbolo.PASTA;
        }
        Simbolo direto = ESCOLHAS.get(chave.trim());
        if (direto != null) {
            return direto;
        }
        return switch (chave.trim()) {
            case "📁", "📂" -> Simbolo.PASTA;
            case "📝", "📄" -> Simbolo.TEXTO;
            case "💻", "⚙" -> Simbolo.CODIGO;
            case "🗄", "📊" -> Simbolo.BANCO;
            case "🖥" -> Simbolo.SERVIDOR;
            case "🔑", "🔐" -> Simbolo.CHAVE;
            case "🔒" -> Simbolo.ESCUDO;
            case "🔗", "🌐" -> Simbolo.ELO;
            case "🏢" -> Simbolo.EMPRESA;
            case "🛠", "🧩" -> Simbolo.FERRAMENTA;
            case "🛒", "🧾" -> Simbolo.CARRINHO;
            case "💡", "✨" -> Simbolo.IDEIA;
            case "☕" -> Simbolo.CAFE;
            case "⭐" -> Simbolo.ESTRELA;
            case "📅", "🗓" -> Simbolo.CALENDARIO;
            default -> Simbolo.PASTA;
        };
    }

    /** O ícone de uma categoria, resolvido a partir do que está no banco. */
    public static Node deCategoria(String chave, double tamanho, String... classes) {
        return de(porChave(chave), tamanho, classes);
    }
}
