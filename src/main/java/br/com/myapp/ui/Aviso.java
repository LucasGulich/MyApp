package br.com.myapp.ui;

import br.com.myapp.core.Log;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * O retorno na tela depois de uma ação: "Lembrete criado", "Nota excluída".
 *
 * <p>Uma ação que não responde nada deixa dúvida — salvou? o clique pegou?
 * Abrir um diálogo para dizer "pronto" resolve a dúvida, mas cobra um segundo
 * clique só para fechar uma notícia boa. O aviso aqui é o meio-termo: aparece
 * no canto, informa e some sozinho, sem interromper.
 *
 * <p>É <b>global</b> de propósito. Cada tela emitindo o seu próprio feedback,
 * cada uma de um jeito, é como o aplicativo fica com cinco linguagens
 * diferentes para a mesma coisa. Aqui existe uma só, e qualquer módulo novo a
 * herda de graça:
 *
 * <pre>
 *   Aviso.sucesso("Lembrete criado com sucesso!");
 *   Aviso.erro("Não foi possível salvar a nota.");
 *   Aviso.atencao("A senha mestra ainda não foi criada.");
 *   Aviso.info("Backup automático ligado.");
 * </pre>
 *
 * <h2>Quando usar cada um</h2>
 *
 * <table border="1">
 *   <caption>Escolha do tipo</caption>
 *   <tr><th>Tipo</th><th>Para quê</th></tr>
 *   <tr><td>sucesso</td><td>A ação deu certo e não precisa de mais nada</td></tr>
 *   <tr><td>erro</td><td>A ação falhou, mas a explicação cabe em uma linha</td></tr>
 *   <tr><td>atencao</td><td>Deu certo com ressalva, ou algo exige cuidado</td></tr>
 *   <tr><td>info</td><td>Só um recado, sem julgamento de valor</td></tr>
 * </table>
 *
 * <p>Erro que precisa de explicação longa, ou que exige uma decisão, continua
 * sendo caso de {@link Dialogos} — o aviso some sozinho, e o que o usuário
 * precisa ler com calma não pode sumir sozinho.
 */
public final class Aviso {

    /** Quanto tempo cada tipo fica na tela. Erro fica mais: dá o que pensar. */
    private static final Duration DURACAO_NORMAL = Duration.seconds(3.2);
    private static final Duration DURACAO_ERRO = Duration.seconds(5);

    /** Além disto, os mais antigos saem para os novos caberem. */
    private static final int MAXIMO_NA_TELA = 4;

    public enum Tipo {
        SUCESSO(Icone.Simbolo.SUCESSO, "aviso-sucesso"),
        ERRO(Icone.Simbolo.ERRO, "aviso-erro"),
        ATENCAO(Icone.Simbolo.ATENCAO, "aviso-atencao"),
        INFO(Icone.Simbolo.INFORMACAO, "aviso-info");

        private final Icone.Simbolo simbolo;
        private final String estilo;

        Tipo(Icone.Simbolo simbolo, String estilo) {
            this.simbolo = simbolo;
            this.estilo = estilo;
        }
    }

    /**
     * A coluna onde os avisos se empilham.
     *
     * <p>Fica sobre a janela principal, no alto à direita. É montada uma vez,
     * em {@link #instalar}, e vive enquanto o aplicativo viver.
     */
    private static VBox pilha;

    private Aviso() {
    }

    // ---------------------------------------------------------- instalação

    /**
     * Prepara a camada de avisos sobre a janela.
     *
     * <p>Recebe a pilha que está na raiz da cena — os avisos precisam ficar
     * <i>por cima</i> do conteúdo, e não empurrá-lo para o lado.
     */
    public static void instalar(StackPane raiz) {
        pilha = new VBox(10);
        pilha.getStyleClass().add("pilha-avisos");
        pilha.setAlignment(Pos.TOP_RIGHT);
        pilha.setPadding(new Insets(52, 20, 20, 20));

        // Três cuidados para a camada não bloquear a janela que ela cobre —
        // e os três são necessários:
        //
        //  1. sem isto a StackPane estica a coluna até o tamanho máximo dela,
        //     que é infinito, e o que era um canto passa a ser a tela toda;
        //  2. pickOnBounds vem ligado em Region: sem desligar, o retângulo
        //     inteiro captura o mouse mesmo onde não há nada desenhado;
        //  3. a folha de estilo não pode dar fundo algum a esta camada, nem
        //     "transparent" — um preenchimento transparente continua sendo um
        //     preenchimento, e é picotado pelo mouse como qualquer outro.
        //
        // Sem os três, o clique morre aqui e a janela inteira fica inerte.
        pilha.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pilha.setPickOnBounds(false);
        StackPane.setAlignment(pilha, Pos.TOP_RIGHT);

        raiz.getChildren().add(pilha);
    }

    // -------------------------------------------------------------- atalhos

    public static void sucesso(String mensagem) {
        mostrar(Tipo.SUCESSO, mensagem);
    }

    public static void erro(String mensagem) {
        mostrar(Tipo.ERRO, mensagem);
    }

    public static void atencao(String mensagem) {
        mostrar(Tipo.ATENCAO, mensagem);
    }

    public static void info(String mensagem) {
        mostrar(Tipo.INFO, mensagem);
    }

    // -------------------------------------------------------------- exibição

    /**
     * Mostra o aviso.
     *
     * <p>Pode ser chamado de qualquer linha de execução: se não estivermos na
     * do JavaFX, a exibição é agendada para ela. Isso importa porque avisos
     * nascem de coisas que acontecem fora da tela — um backup que terminou,
     * um lembrete que disparou.
     */
    public static void mostrar(Tipo tipo, String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> mostrar(tipo, mensagem));
            return;
        }
        if (pilha == null) {
            // Sem janela montada (testes, inicialização) o aviso vira registro:
            // a informação não se perde, só muda de lugar.
            Log.info("Aviso [" + tipo + "]: " + mensagem);
            return;
        }

        Node cartao = montar(tipo, mensagem);
        pilha.getChildren().add(cartao);

        while (pilha.getChildren().size() > MAXIMO_NA_TELA) {
            pilha.getChildren().remove(0);
        }

        animar(cartao, tipo == Tipo.ERRO ? DURACAO_ERRO : DURACAO_NORMAL);
    }

    private static Node montar(Tipo tipo, String mensagem) {
        Label texto = new Label(mensagem);
        texto.getStyleClass().add("aviso-texto");
        texto.setWrapText(true);
        texto.setMaxWidth(300);
        HBox.setHgrow(texto, Priority.ALWAYS);

        Region faixa = new Region();
        faixa.getStyleClass().add("aviso-faixa");

        HBox cartao = new HBox(12,
                faixa,
                Icone.de(tipo.simbolo, 20, "icone-aviso"),
                texto);
        cartao.getStyleClass().addAll("aviso", tipo.estilo);
        cartao.setAlignment(Pos.CENTER_LEFT);
        cartao.setMaxWidth(Region.USE_PREF_SIZE);

        // Clicar dispensa: quem já leu não precisa esperar.
        cartao.setOnMouseClicked(e -> sair(cartao, Duration.ZERO));
        return cartao;
    }

    /**
     * Entrada deslizando da direita, pausa, saída desaparecendo.
     *
     * <p>O movimento curto (24 px) e rápido é o que faz o olho perceber que
     * algo novo chegou sem que a animação vire espetáculo.
     */
    private static void animar(Node cartao, Duration permanencia) {
        cartao.setOpacity(0);
        cartao.setTranslateX(24);

        FadeTransition entrada = new FadeTransition(Duration.millis(180), cartao);
        entrada.setToValue(1);

        TranslateTransition desliza = new TranslateTransition(Duration.millis(220), cartao);
        desliza.setToX(0);
        desliza.setInterpolator(Interpolator.EASE_OUT);
        desliza.play();

        entrada.setOnFinished(e -> sair(cartao, permanencia));
        entrada.play();
    }

    private static void sair(Node cartao, Duration espera) {
        PauseTransition pausa = new PauseTransition(espera);

        FadeTransition saida = new FadeTransition(Duration.millis(220), cartao);
        saida.setToValue(0);

        TranslateTransition recua = new TranslateTransition(Duration.millis(220), cartao);
        recua.setToX(24);

        SequentialTransition sequencia = new SequentialTransition(pausa, saida);
        sequencia.setOnFinished(e -> {
            if (pilha != null) {
                pilha.getChildren().remove(cartao);
            }
        });
        pausa.setOnFinished(e -> recua.play());
        sequencia.play();
    }
}
