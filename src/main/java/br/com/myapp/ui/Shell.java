package br.com.myapp.ui;

import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.modules.AppModule;
import br.com.myapp.modules.ModuleRegistry;
import br.com.myapp.security.SecurityService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Esqueleto da janela: menu lateral a esquerda, conteúdo do módulo a direita.
 *
 * O menu e montado a partir do {@link ModuleRegistry}, sem nada escrito a mao.
 * Registrar um módulo novo já o faz aparecer aqui, na posição definida pela
 * ordem dele.
 *
 * Quando o aplicativo tranca, o conteúdo e substituido pela tela de senha; as
 * telas dos módulos ficam guardadas em memória e voltam como estavam.
 */
public class Shell extends BorderPane {

    /**
     * Pedido para abrir outro módulo.
     *
     * Uma tela nunca troca o módulo por conta própria: ela publica o pedido e
     * o Shell decide. Sem isso, a tela inicial precisaria conhecer o Shell, e
     * um módulo passaria a depender de quem o hospeda.
     */
    public record PedidoDeNavegacao(String moduloId) {
    }

    private final StackPane area = new StackPane();
    private final VBox menu = new VBox();
    private final Map<String, Button> botoesMenu = new LinkedHashMap<>();
    private final Map<String, Node> telasEmCache = new HashMap<>();

    private final Button botaoConfiguracoes = new Button();
    private final Button botaoTrancar = new Button();

    private final Label marca = new Label();
    private final HBox linhaDaMarca = new HBox(8);
    private final Button botaoRecolher = new Button();

    private Tooltip dicaDoRecolher;

    /** Marca no botão para a dica do menu recolhido não ser instalada duas vezes. */
    private static final String DICA_INSTALADA = "myapp.dica-instalada";

    private final Runnable aoSair;
    private String moduloAtual;
    private boolean mostrandoBloqueio;

    public Shell(Runnable aoSair) {
        this.aoSair = aoSair;
        getStyleClass().add("raiz");
        aplicarTema();

        setLeft(montarMenu());
        setCenter(area);

        // A janela reage sozinha ao trancar e destrancar, venha de onde vier:
        // botao, atalho, inatividade ou menu da bandeja.
        EventBus.ouvir(SecurityService.EstadoMudou.class, evento ->
                Platform.runLater(() -> {
                    ModuleRegistry.notificarBloqueio(evento.destrancado());
                    if (evento.destrancado()) {
                        habilitarNavegacao(true);
                        if (mostrandoBloqueio) {
                            mostrandoBloqueio = false;
                            abrirModulo(moduloAtual != null ? moduloAtual : primeiroModuloId());
                        }
                    } else {
                        PopupAlerta.fecharTodos();
                        mostrarBloqueio();
                    }
                }));

        // Uma tela pode pedir para abrir outro módulo.
        EventBus.ouvir(PedidoDeNavegacao.class, pedido ->
                Platform.runLater(() -> abrirModulo(pedido.moduloId())));

        abrirModulo(primeiroModuloId());
    }

    /**
     * Aplica o tema salvo nas configurações.
     *
     * Marca este nó e também a raiz da cena. Os dois carregam a classe
     * {@code raiz}, que é onde as cores do tema são declaradas — e uma
     * declaração de cor só vale para os descendentes de quem a declara. Se
     * apenas um dos dois fosse marcado, o outro continuaria distribuindo as
     * cores do tema anterior para o pedaço de tela que ele cobre: era assim
     * que a barra de título continuava escura no tema claro.
     */
    public final void aplicarTema() {
        marcarTema(this);
        if (getScene() != null && getScene().getRoot() != null) {
            marcarTema(getScene().getRoot());
        }
    }

    /**
     * Põe ou tira a classe do tema claro em um nó.
     *
     * Vale para qualquer nó que carregue a classe {@code raiz}: sem esta
     * marca, ele declara as cores do tema escuro para tudo o que está abaixo
     * dele, independentemente do que esteja configurado.
     */
    public static void marcarTema(Parent no) {
        no.getStyleClass().remove("claro");
        if ("claro".equals(Config.get().tema)) {
            no.getStyleClass().add("claro");
        }
    }

    // ------------------------------------------------------------ menu lateral

    private VBox montarMenu() {
        menu.getStyleClass().add("menu-lateral");

        marca.setText("MyApp");
        marca.getStyleClass().add("marca");
        marca.setGraphic(Icone.de(Icone.Simbolo.MARCA, 22));

        Region empurra = new Region();
        HBox.setHgrow(empurra, Priority.ALWAYS);

        botaoRecolher.getStyleClass().addAll("botao-icone-miudo", "botao-recolher");
        botaoRecolher.setOnAction(e -> recolher(!Config.get().menuRecolhido));

        linhaDaMarca.getChildren().addAll(marca, empurra, botaoRecolher);
        linhaDaMarca.setAlignment(Pos.CENTER_LEFT);
        linhaDaMarca.getStyleClass().add("linha-marca");
        menu.getChildren().add(linhaDaMarca);

        for (AppModule modulo : ModuleRegistry.todos()) {
            Button botao = new Button(modulo.nome());
            botao.getStyleClass().add("item-menu");
            botao.setGraphic(Icone.de(modulo.icone(), 18));
            botao.setOnAction(e -> abrirModulo(modulo.id()));
            botoesMenu.put(modulo.id(), botao);
            menu.getChildren().add(botao);
        }

        Region espaco = new Region();
        VBox.setVgrow(espaco, Priority.ALWAYS);
        menu.getChildren().add(espaco);

        // --- rodape do menu ---
        botaoConfiguracoes.setText("Configurações");
        botaoConfiguracoes.getStyleClass().add("item-menu");
        botaoConfiguracoes.setGraphic(Icone.de(Icone.Simbolo.CONFIGURACOES, 18));
        botaoConfiguracoes.setOnAction(e -> abrirConfiguracoes());

        botaoTrancar.setText("Trancar");
        botaoTrancar.getStyleClass().add("item-menu");
        botaoTrancar.setGraphic(Icone.de(Icone.Simbolo.CADEADO, 18));
        botaoTrancar.setTooltip(new Tooltip("Tranca o aplicativo agora (Ctrl+L)"));
        botaoTrancar.setOnAction(e -> SecurityService.trancar());

        menu.getChildren().addAll(new Region(), botaoConfiguracoes, botaoTrancar);

        recolher(Config.get().menuRecolhido);
        return menu;
    }

    /**
     * Recolhe a barra lateral para uma faixa de ícones, ou a devolve inteira.
     *
     * <p>Serve para dois problemas ao mesmo tempo: dá quase duzentos pixels de
     * volta ao conteúdo — que fazem falta nas telas de três colunas com a
     * janela sem maximizar — e atende quem já sabe de cor o que cada ícone
     * faz e não quer o texto ocupando espaço.
     *
     * <p>Recolhida, cada item ganha a sua dica: ícone sem rótulo e sem
     * explicação seria adivinhação.
     */
    private void recolher(boolean recolhido) {
        // Só grava quando muda de verdade: senão o arquivo de configuração
        // seria reescrito a cada abertura do aplicativo, sem nada ter mudado.
        if (Config.get().menuRecolhido != recolhido) {
            Config.get().menuRecolhido = recolhido;
            Config.get().salvar();
        }

        menu.getStyleClass().remove("recolhido");
        if (recolhido) {
            menu.getStyleClass().add("recolhido");
        }

        marca.setVisible(!recolhido);
        marca.setManaged(!recolhido);
        linhaDaMarca.setAlignment(recolhido ? Pos.CENTER : Pos.CENTER_LEFT);

        Botoes.trocarIcone(botaoRecolher,
                recolhido ? Icone.Simbolo.AVANCAR : Icone.Simbolo.VOLTAR);

        if (dicaDoRecolher == null) {
            dicaDoRecolher = Botoes.instalarDica(botaoRecolher, "");
        }
        dicaDoRecolher.setText(recolhido
                ? "Expandir o menu"
                : "Recolher o menu, para sobrar espaço na tela");

        for (AppModule modulo : ModuleRegistry.todos()) {
            ajustarItem(botoesMenu.get(modulo.id()), modulo.nome(), recolhido);
        }
        ajustarItem(botaoConfiguracoes, "Configurações", recolhido);
        ajustarItem(botaoTrancar, "Trancar o aplicativo agora (Ctrl+L)", recolhido);
    }

    private void ajustarItem(Button botao, String nome, boolean recolhido) {
        if (botao == null) {
            return;
        }
        botao.setContentDisplay(recolhido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        botao.setAlignment(recolhido ? Pos.CENTER : Pos.CENTER_LEFT);

        // A dica entra uma vez só. Instalar a cada recolhimento empilharia um
        // tratador de evento por vez, e depois de alternar algumas vezes o
        // mesmo botão abriria várias dicas sobrepostas.
        if (recolhido && botao.getProperties().putIfAbsent(DICA_INSTALADA, true) == null) {
            Botoes.instalarDica(botao, nome);
        }
    }

    /**
     * Liga e desliga tudo o que não seja a tela de senha.
     *
     * Trancar precisa trancar de verdade. Antes, o conteúdo era trocado pela
     * tela de senha mas o menu continuava respondendo: dava para navegar entre
     * os módulos e até abrir as Configurações com o aplicativo bloqueado.
     *
     * Com o menu desabilitado, o único caminho é digitar a senha.
     */
    private void habilitarNavegacao(boolean habilitada) {
        botoesMenu.values().forEach(b -> b.setDisable(!habilitada));
        botaoConfiguracoes.setDisable(!habilitada);

        // Trancar de novo não faz sentido com o app já trancado.
        botaoTrancar.setDisable(!habilitada);

        // O esmaecido explica por que os botões pararam de responder.
        menu.getStyleClass().remove("trancado");
        if (!habilitada) {
            menu.getStyleClass().add("trancado");
        }
    }

    private String primeiroModuloId() {
        return ModuleRegistry.todos().isEmpty() ? null : ModuleRegistry.todos().get(0).id();
    }

    // ------------------------------------------------------------- navegacao

    /** Troca o conteúdo para o módulo indicado. */
    public void abrirModulo(String id) {
        if (id == null) {
            return;
        }
        ModuleRegistry.porId(id).ifPresent(modulo -> {
            if (modulo.exigeDesbloqueio() && !SecurityService.estaDestrancado()) {
                mostrarBloqueio();
                return;
            }
            Node tela = telasEmCache.computeIfAbsent(id, chave -> modulo.criarTela());
            modulo.aoExibir();

            area.getChildren().setAll(tela);
            moduloAtual = id;
            mostrandoBloqueio = false;
            destacarMenu(id);
        });
    }

    private void destacarMenu(String id) {
        botoesMenu.forEach((chave, botao) -> {
            botao.getStyleClass().remove("ativo");
            if (chave.equals(id)) {
                botao.getStyleClass().add("ativo");
            }
        });
    }

    /** Substitui o conteúdo pela tela de senha. */
    public void mostrarBloqueio() {
        mostrandoBloqueio = true;
        botoesMenu.values().forEach(b -> b.getStyleClass().remove("ativo"));
        habilitarNavegacao(false);

        TelaSenha tela = new TelaSenha(TelaSenha.Modo.BLOQUEIO, v -> {
            // O evento do EventBus cuida de reabrir o módulo.
        });
        area.getChildren().setAll(tela);
    }

    /** Tela de primeira execução, para criar a senha mestra. */
    public void mostrarCadastroDeSenha(Runnable aoConcluir) {
        mostrandoBloqueio = true;
        // Nenhum módulo está aberto ainda: o menu não deve destacar nada, e
        // nada pode ser aberto antes de a senha existir.
        botoesMenu.values().forEach(b -> b.getStyleClass().remove("ativo"));
        habilitarNavegacao(false);

        TelaSenha tela = new TelaSenha(TelaSenha.Modo.CADASTRO, v -> {
            mostrandoBloqueio = false;
            habilitarNavegacao(true);
            abrirModulo(primeiroModuloId());
            aoConcluir.run();
        });
        area.getChildren().setAll(tela);
    }

    // -------------------------------------------------------- Configurações

    private void abrirConfiguracoes() {
        try {
            new TelaConfiguracoes(getScene().getWindow(), this::aoTrocarTema).abrir();
        } catch (Exception e) {
            Log.erro("Falha ao abrir as configurações", e);
        }
    }

    private void aoTrocarTema() {
        aplicarTema();
    }

    /** Cabeçalho reutilizável para as telas dos módulos. */
    public static HBox cabecalho(String titulo, Node... acoes) {
        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("titulo-tela");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox linha = new HBox(12, rotulo, espaco);
        linha.getChildren().addAll(acoes);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.setPadding(new Insets(0, 0, 18, 0));
        return linha;
    }

    public Runnable acaoDeSair() {
        return aoSair;
    }
}
