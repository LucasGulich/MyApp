package br.com.myapp.ui;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * As caixas de diálogo do aplicativo: confirmar, avisar, informar.
 *
 * <p>São <b>globais</b> por decisão: "tem certeza que quer excluir?" é a
 * mesma pergunta em lembretes, notas, categorias e recados, e deve ter
 * sempre o mesmo desenho, as mesmas palavras nos botões e o mesmo lugar para
 * cada botão. Quando cada tela monta a sua, a diferença aparece justamente no
 * momento em que o usuário está prestes a apagar algo — o pior momento para
 * ele precisar reler onde fica o "cancelar".
 *
 * <pre>
 *   if (Dialogos.confirmarExclusao(janela, "o lembrete \"Reunião\"")) { ... }
 *
 *   Dialogos.erro(janela, "Não foi possível salvar", e.getMessage());
 *   Dialogos.info(janela, "Backup gerado", "Arquivo salvo em: ...");
 * </pre>
 *
 * <h2>Diálogo ou aviso?</h2>
 *
 * O diálogo <b>interrompe</b> e espera resposta; use quando há uma decisão a
 * tomar ou algo que precisa ser lido com calma. Para confirmar que deu certo,
 * o certo é {@link Aviso}, que informa sem cobrar um clique.
 *
 * <h2>Por que não o Alert do JavaFX</h2>
 *
 * O {@code Alert} pronto traz a aparência do sistema: painel claro, botões
 * nativos, barra de título branca do Windows. Dava para remendar com estilo,
 * mas sempre sobrava uma peça interna fora do tema. Desenhar a caixa aqui
 * custa algumas dezenas de linhas e entrega controle total — inclusive o
 * botão vermelho de excluir, que o Alert não tem.
 */
public final class Dialogos {

    private Dialogos() {
    }

    // ---------------------------------------------------------- perguntas

    /** Pergunta de sim ou não, com botões neutros. */
    public static boolean confirmar(Window dono, String titulo, String mensagem) {
        return perguntar(dono, Icone.Simbolo.PERGUNTA, "dialogo-pergunta",
                titulo, mensagem, "Confirmar", false);
    }

    /** Pergunta de sim ou não com o texto do botão escolhido por quem chama. */
    public static boolean confirmar(Window dono, String titulo, String mensagem,
                                    String textoDoBotao) {
        return perguntar(dono, Icone.Simbolo.PERGUNTA, "dialogo-pergunta",
                titulo, mensagem, textoDoBotao, false);
    }

    /**
     * A pergunta antes de excluir.
     *
     * <p>Tem desenho próprio — selo vermelho e botão vermelho — porque o custo
     * de errar aqui é diferente do de errar em qualquer outra confirmação. E
     * o texto já lembra que nada se perde de verdade, que é o comportamento
     * real do aplicativo e costuma mudar a resposta.
     *
     * @param oQue descrito como cai na frase: {@code "o lembrete \"Reunião\""}
     */
    public static boolean confirmarExclusao(Window dono, String oQue) {
        return confirmarExclusao(dono, oQue,
                "O item vai para a lixeira e pode ser restaurado depois.");
    }

    public static boolean confirmarExclusao(Window dono, String oQue, String detalhe) {
        return perguntar(dono, Icone.Simbolo.EXCLUIR, "dialogo-perigo",
                "Excluir " + oQue + "?", detalhe, "Excluir", true);
    }

    // ------------------------------------------------------------- recados

    public static void erro(Window dono, String titulo, String mensagem) {
        contar(dono, Icone.Simbolo.ERRO, "dialogo-perigo", titulo, mensagem);
    }

    public static void info(Window dono, String titulo, String mensagem) {
        contar(dono, Icone.Simbolo.INFORMACAO, "dialogo-info", titulo, mensagem);
    }

    public static void atencao(Window dono, String titulo, String mensagem) {
        contar(dono, Icone.Simbolo.ATENCAO, "dialogo-atencao", titulo, mensagem);
    }

    public static void sucesso(Window dono, String titulo, String mensagem) {
        contar(dono, Icone.Simbolo.SUCESSO, "dialogo-sucesso", titulo, mensagem);
    }

    // -------------------------------------------------------------- montagem

    private static void contar(Window dono, Icone.Simbolo simbolo, String estilo,
                               String titulo, String mensagem) {
        Caixa caixa = new Caixa(dono, simbolo, estilo, titulo, mensagem);

        Button ok = new Button("Entendi");
        ok.getStyleClass().add("botao-primario");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> caixa.fechar(true));

        caixa.comBotoes(ok);
        caixa.abrir();
    }

    private static boolean perguntar(Window dono, Icone.Simbolo simbolo, String estilo,
                                     String titulo, String mensagem,
                                     String textoDoBotao, boolean perigoso) {
        Caixa caixa = new Caixa(dono, simbolo, estilo, titulo, mensagem);

        Button cancelar = new Button("Cancelar");
        cancelar.getStyleClass().add("botao");
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> caixa.fechar(false));

        Button confirmar = new Button(textoDoBotao);
        confirmar.getStyleClass().add(perigoso ? "botao-perigo-cheio" : "botao-primario");
        confirmar.setOnAction(e -> caixa.fechar(true));

        // O foco começa no cancelar quando a ação é destrutiva: um Enter
        // distraído não pode excluir nada.
        caixa.comBotoes(cancelar, confirmar);
        caixa.focarPrimeiro(perigoso ? cancelar : confirmar);
        return caixa.abrir();
    }

    /** A janelinha em si, montada à mão para seguir o tema por inteiro. */
    private static final class Caixa {

        private final Stage palco = new Stage();
        private final VBox raiz = new VBox();
        private final HBox acoes = new HBox(10);
        private boolean resposta;
        private Node primeiroFoco;

        Caixa(Window dono, Icone.Simbolo simbolo, String estilo,
              String titulo, String mensagem) {

            palco.initStyle(StageStyle.UNDECORATED);
            palco.initModality(Modality.APPLICATION_MODAL);
            if (dono != null) {
                palco.initOwner(dono);
            }
            palco.setTitle("MyApp");

            StackPane selo = new StackPane(Icone.de(simbolo, 22, "icone-selo"));
            selo.getStyleClass().add("dialogo-selo");

            Label rotulo = new Label(titulo);
            rotulo.getStyleClass().add("dialogo-titulo");
            rotulo.setWrapText(true);

            VBox textos = new VBox(6, rotulo);
            if (mensagem != null && !mensagem.isBlank()) {
                Label corpo = new Label(mensagem);
                corpo.getStyleClass().add("dialogo-mensagem");
                corpo.setWrapText(true);
                textos.getChildren().add(corpo);
            }
            HBox.setHgrow(textos, Priority.ALWAYS);

            HBox conteudo = new HBox(16, selo, textos);
            conteudo.getStyleClass().add("dialogo-corpo");
            conteudo.setAlignment(Pos.TOP_LEFT);

            acoes.getStyleClass().add("dialogo-acoes");
            acoes.setAlignment(Pos.CENTER_RIGHT);

            raiz.getStyleClass().addAll("raiz", "caixa-dialogo", estilo);
            if ("claro".equals(Config.get().tema)) {
                raiz.getStyleClass().add("claro");
            }
            raiz.getChildren().addAll(
                    BarraTitulo.paraDialogo(palco, "MyApp"),
                    conteudo,
                    acoes);
        }

        void comBotoes(Button... botoes) {
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            acoes.getChildren().add(espaco);
            acoes.getChildren().addAll(botoes);
        }

        void focarPrimeiro(Node no) {
            this.primeiroFoco = no;
        }

        void fechar(boolean valor) {
            this.resposta = valor;
            palco.close();
        }

        boolean abrir() {
            Scene cena = new Scene(raiz);
            cena.getStylesheets().add(
                    Dialogos.class.getResource("/css/app.css").toExternalForm());

            // Esc sempre cancela, mesmo sem botão de cancelar em foco.
            cena.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    fechar(false);
                }
            });

            palco.setScene(cena);
            palco.setMinWidth(400);
            palco.setResizable(false);
            palco.sizeToScene();

            // O tamanho da janela só é conhecido depois de ela existir, e é
            // ele que define onde centralizar. Para não aparecer no lugar
            // errado e pular, a caixa nasce invisível e só ganha opacidade
            // depois de posicionada.
            palco.setOpacity(0);
            palco.setOnShown(e -> {
                centralizar();
                palco.setOpacity(1);
                if (primeiroFoco != null) {
                    primeiroFoco.requestFocus();
                }
            });

            palco.showAndWait();
            return resposta;
        }

        /**
         * Centraliza sobre a janela que abriu o diálogo, não sobre o monitor.
         *
         * <p>Com dois monitores, centralizar na tela joga a pergunta longe de
         * onde o olho está. E o terço superior, em vez da metade exata, é onde
         * o olho procura uma pergunta.
         */
        private void centralizar() {
            Window dono = palco.getOwner();
            if (dono == null || Double.isNaN(dono.getWidth()) || dono.getWidth() <= 0) {
                palco.centerOnScreen();
                return;
            }
            palco.setX(dono.getX() + (dono.getWidth() - palco.getWidth()) / 2);
            palco.setY(dono.getY() + (dono.getHeight() - palco.getHeight()) / 3);
        }
    }

    // ------------------------------------------- tema nos diálogos maiores

    /**
     * Aplica a folha de estilo do aplicativo a um painel de diálogo.
     *
     * <p>Os editores maiores — lembrete, nota, categoria, configurações —
     * continuam usando o {@code Dialog} do JavaFX, porque são formulários
     * inteiros e não caberiam na caixa simples daqui. Este método é o que faz
     * esses formulários seguirem o mesmo tema.
     */
    /**
     * Ctrl+S aperta o botão de salvar de um formulário de cadastro.
     *
     * <p>Vale de qualquer campo do formulário, inclusive de dentro de um texto
     * longo: o filtro fica no painel, e o painel recebe a tecla antes do campo
     * em foco.
     *
     * <p>O atalho <b>aperta o botão</b>, em vez de fechar o diálogo por conta
     * própria. Assim passa pelas mesmas validações do clique — o que está
     * errado continua sendo avisado, e o diálogo continua aberto.
     *
     * <p>Não é ligado em {@link #aplicarTema} de propósito: cada formulário
     * pede o seu. Um Ctrl+S que valesse em qualquer diálogo confirmaria também
     * uma exclusão, e ali o atalho não pode existir.
     */
    public static void salvarComCtrlS(DialogPane painel, ButtonType salvar) {
        Button botao = (Button) painel.lookupButton(salvar);
        if (botao == null) {
            return;
        }
        Botoes.instalarDica(botao, botao.getText() + " (Ctrl+S)");

        painel.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.S || !e.isShortcutDown() || e.isAltDown() || e.isShiftDown()) {
                return;
            }
            e.consume();
            if (botao.isDisabled()) {
                return;
            }
            confirmarEdicaoEmCurso(painel);
            botao.fire();
        });
    }

    /**
     * Grava o valor que ainda está sendo digitado.
     *
     * <p>Um contador ({@code Spinner}) ou uma data digitada só passam a valer
     * quando o campo perde o foco. Com o clique no botão, o foco sai do campo
     * antes do salvar; com o atalho, não sai — e o número digitado seria
     * ignorado sem aviso.
     */
    private static void confirmarEdicaoEmCurso(DialogPane painel) {
        if (painel.getScene() == null) {
            return;
        }
        for (Node no = painel.getScene().getFocusOwner(); no != null; no = no.getParent()) {
            try {
                if (no instanceof Spinner<?> contador) {
                    contador.commitValue();
                    return;
                }
                if (no instanceof DatePicker data) {
                    data.commitValue();
                    return;
                }
                if (no instanceof ComboBox<?> lista && lista.isEditable()) {
                    lista.commitValue();
                    return;
                }
            } catch (RuntimeException invalido) {
                // Texto que não vira valor (letra num contador): fica o valor
                // anterior, como aconteceria ao sair do campo.
                return;
            }
        }
    }

    public static void aplicarTema(DialogPane painel) {
        painel.getStylesheets().add(
                Dialogos.class.getResource("/css/app.css").toExternalForm());
        painel.getStyleClass().add("raiz");
        if ("claro".equals(Config.get().tema)) {
            painel.getStyleClass().add("claro");
        }
        trocarBarraDeTitulo(painel);
    }

    /**
     * Substitui a barra de título do Windows por uma que segue o tema.
     *
     * <p>O diálogo só ganha janela quando está prestes a aparecer, e
     * {@code initStyle} precisa ser chamado antes disso — por isso a troca
     * acontece dentro de um ouvinte, e não aqui direto.
     *
     * <p>Se qualquer parte falhar, o diálogo continua funcionando com a barra
     * nativa: feio, mas utilizável. Aparência nunca deve impedir o uso.
     */
    private static void trocarBarraDeTitulo(DialogPane painel) {
        painel.sceneProperty().addListener((obs, anterior, cena) -> {
            if (cena == null) {
                return;
            }
            cena.windowProperty().addListener((o2, a2, janela) -> {
                if (!(janela instanceof Stage palco) || palco.isShowing()) {
                    return;
                }
                try {
                    palco.initStyle(StageStyle.UNDECORATED);

                    String titulo = palco.getTitle() == null || palco.getTitle().isBlank()
                            ? "MyApp" : palco.getTitle();
                    painel.setHeader(BarraTitulo.paraDialogo(palco, titulo));

                } catch (Exception e) {
                    Log.aviso("Mantendo a barra de título do sistema neste diálogo: "
                            + e.getMessage());
                }
            });
        });
    }
}
