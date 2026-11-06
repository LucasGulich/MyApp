package br.com.myapp.modules.atalhos;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.core.Texto;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Arrastavel;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.CampoBusca;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Navegadores;
import br.com.myapp.ui.Secao;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A tela de atalhos: grupos com blocos que abrem programas, pastas, sites e
 * arquivos com um clique.
 *
 * <pre>
 *   Atalhos                      [🔍 Buscar e Enter para abrir] [+ Grupo] [+ Atalho]
 *   ▦ APLICATIVOS DO DIA A DIA ──────────────────────── [+ Adicionar] [✎] [🗑]
 *   ┌──────┐ ┌──────┐ ┌──────┐
 *   │ icone│ │ icone│ │ icone│
 *   │Teams │ │Chrome│ │DBeav.│
 *   └──────┘ └──────┘ └──────┘
 * </pre>
 *
 * <p>Clique abre. Botão direito: editar, mostrar na pasta, copiar o caminho,
 * mover de grupo, excluir. Arrastar reordena dentro do grupo. Arrastar um
 * programa, pasta ou link do Explorador ou do navegador para cima de um
 * grupo cria o atalho ali. Ver {@code docs/ATALHOS.md}.
 */
public class AtalhosView extends BorderPane {

    private final AtalhoService servico = new AtalhoService();
    private final VBox areaGrupos = new VBox(22);
    private final CampoBusca busca = new CampoBusca("Buscar… (Enter abre o primeiro)");
    private final Label contador = new Label();
    private List<GrupoAtalho> grupos = List.of();

    public AtalhosView() {
        getStyleClass().add("conteudo");

        servico.criarGruposIniciaisNaPrimeiraVez();
        // Lido fora da tela; quando chega, os sites ganham o selo do navegador.
        Navegadores.carregarEmSegundoPlano(() -> Platform.runLater(this::desenhar));

        setTop(montarCabecalho());

        areaGrupos.setPadding(new Insets(0, 8, 24, 0));
        ScrollPane rolagem = new ScrollPane(areaGrupos);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("rolagem-formulario");
        setCenter(rolagem);

        busca.textProperty().addListener((obs, antes, agora) -> desenhar());
        busca.aoApertarEnter(this::abrirPrimeiroEncontrado);

        EventBus.ouvir(AtalhoService.AtalhosMudaram.class, e -> Platform.runLater(this::recarregar));
        recarregar();
    }

    public void recarregar() {
        grupos = servico.grupos();
        desenhar();
    }

    // ============================================================ cabeçalho

    private VBox montarCabecalho() {
        Label titulo = new Label("Atalhos");
        titulo.getStyleClass().add("titulo-tela");
        contador.getStyleClass().add("subtitulo");
        VBox textos = new VBox(2, titulo, contador);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        busca.setPrefWidth(280);

        Button novoGrupo = Botoes.comum("Novo grupo", Icone.Simbolo.GRADE);
        novoGrupo.setOnAction(e -> editarGrupo(null));
        Button novoAtalho = Botoes.primario("Novo atalho", Icone.Simbolo.ADICIONAR);
        novoAtalho.setOnAction(e -> editarAtalho(null, grupos.isEmpty() ? -1 : grupos.get(0).getId()));

        HBox linha = new HBox(12, textos, espaco, busca, novoGrupo, novoAtalho);
        linha.setAlignment(Pos.CENTER_LEFT);
        VBox cabecalho = new VBox(linha);
        cabecalho.setPadding(new Insets(0, 0, 18, 0));
        return cabecalho;
    }

    // ============================================================ desenhar

    private void desenhar() {
        String termo = busca.getText().trim();
        boolean filtrando = !termo.isEmpty();
        int total = grupos.stream().mapToInt(g -> g.getAtalhos().size()).sum();
        contador.setText(total == 0 ? "Programas, pastas, sites e arquivos a um clique"
                : total + (total == 1 ? " atalho" : " atalhos") + " em " + grupos.size()
                + (grupos.size() == 1 ? " grupo" : " grupos"));

        areaGrupos.getChildren().clear();
        if (grupos.isEmpty()) {
            areaGrupos.getChildren().add(new EstadoVazio(Icone.Simbolo.GRADE, "Nenhum grupo de atalhos.")
                    .comExplicacao("Um grupo é uma faixa da tela — \"Aplicativos do dia a dia\", "
                            + "\"Pastas na rede\" — com os atalhos lado a lado.")
                    .comAcao("Criar o primeiro grupo", () -> editarGrupo(null))
                    .emCartao());
            return;
        }

        List<Secao> desenhados = new ArrayList<>();
        for (GrupoAtalho g : grupos) {
            List<Atalho> visiveis = filtrando
                    ? g.getAtalhos().stream().filter(a -> Texto.contem(a.getNome(), termo)
                            || Texto.contem(a.getAlvo(), termo)).toList()
                    : g.getAtalhos();
            if (filtrando && visiveis.isEmpty()) {
                continue;
            }
            Secao secao = montarGrupo(g, visiveis, !filtrando);
            desenhados.add(secao);
            areaGrupos.getChildren().add(secao);
        }

        if (filtrando && desenhados.isEmpty()) {
            areaGrupos.getChildren().add(new EstadoVazio(Icone.Simbolo.BUSCAR, "Nada encontrado para \"" + termo + "\".")
                    .comExplicacao("A busca olha o nome e o caminho de cada atalho, sem ligar para acentos.")
                    .emCartao());
            return;
        }

        // Grupos trocam de lugar arrastando — só sem filtro, quando a ordem
        // na tela é a ordem de verdade.
        if (!filtrando && grupos.size() > 1) {
            List<GrupoAtalho> ordem = new ArrayList<>(grupos);
            Arrastavel.instalar(areaGrupos, ordem.size(), (de, para) -> {
                Arrastavel.mover(ordem, de, para);
                servico.reordenarGrupos(ordem);
            });
        }
    }

    private Secao montarGrupo(GrupoAtalho g, List<Atalho> atalhos, boolean podeReordenar) {
        Button adicionar = Botoes.miudo("Adicionar", Icone.Simbolo.ADICIONAR);
        adicionar.setOnAction(e -> editarAtalho(null, g.getId()));
        Button renomear = Botoes.icone(Icone.Simbolo.EDITAR, "Renomear o grupo");
        renomear.getStyleClass().add("botao-icone-miudo");
        renomear.setOnAction(e -> editarGrupo(g));
        Button excluir = Botoes.iconePerigo(Icone.Simbolo.EXCLUIR, "Excluir o grupo");
        excluir.getStyleClass().add("botao-icone-miudo");
        excluir.setOnAction(e -> excluirGrupo(g));

        FlowPane blocos = new FlowPane(10, 10);
        blocos.getStyleClass().add("grade-atalhos");
        for (Atalho a : atalhos) {
            blocos.getChildren().add(bloco(a));
        }

        Node corpo = blocos;
        if (atalhos.isEmpty()) {
            Label dica = new Label("Arraste para cá um programa, uma pasta ou um link — "
                    + "ou clique em Adicionar.");
            dica.getStyleClass().add("texto-fraco");
            dica.setWrapText(true);
            dica.setMinHeight(Region.USE_PREF_SIZE);
            StackPane vazio = new StackPane(dica);
            vazio.getStyleClass().add("grupo-atalhos-vazio");
            corpo = vazio;
        } else if (podeReordenar && atalhos.size() > 1) {
            List<Atalho> ordem = new ArrayList<>(atalhos);
            Arrastavel.instalar(blocos, ordem.size(), (de, para) -> {
                Arrastavel.mover(ordem, de, para);
                servico.reordenar(ordem);
            });
        }

        Secao secao = new Secao(Icone.Simbolo.GRADE, g.getNome().toUpperCase(), corpo)
                .comAcoes(adicionar, renomear, excluir);
        aceitarSoltar(secao, g);
        return secao;
    }

    /** Um bloco: ícone em cima, nome embaixo. Clique abre; botão direito, o menu. */
    private VBox bloco(Atalho a) {
        Node desenho = IconesAtalho.desenho(a, 40);
        StackPane moldura = new StackPane(desenho);
        moldura.getStyleClass().add("moldura-icone-atalho");
        // Favicon costuma ser preto (GitHub, Notion...) e sumiria no tema
        // escuro: em site, o ícone vai sobre uma plaquinha clara.
        if (a.getTipo() == TipoAtalho.SITE && a.getModoIcone() == Atalho.ModoIcone.AUTOMATICO
                && a.getIconePng() != null) {
            moldura.getStyleClass().add("placa-clara");
        }

        Label nome = new Label(a.getNome());
        nome.getStyleClass().add("nome-atalho");
        nome.setWrapText(true);
        nome.setMaxWidth(92);
        nome.setMinHeight(Region.USE_PREF_SIZE);
        nome.setAlignment(Pos.TOP_CENTER);
        nome.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Site com navegador escolhido: o ícone dele, pequeno, no canto.
        String abreNo = "";
        if (a.getTipo() == TipoAtalho.SITE && !a.getNavegador().isBlank()) {
            Optional<Navegadores.Navegador> nav = Navegadores.jaLido(a.getNavegador());
            abreNo = " · abre no " + nav.map(Navegadores.Navegador::nome)
                    .orElse(new java.io.File(a.getNavegador()).getName());
            nav.map(Navegadores.Navegador::icone).ifPresent(png -> {
                javafx.scene.image.ImageView selo = new javafx.scene.image.ImageView(new javafx.scene.image.Image(
                        new java.io.ByteArrayInputStream(png), 36, 36, true, true));
                selo.setFitWidth(18);
                selo.setFitHeight(18);
                StackPane fundoSelo = new StackPane(selo);
                fundoSelo.getStyleClass().add("selo-navegador");
                fundoSelo.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
                StackPane.setAlignment(fundoSelo, Pos.BOTTOM_RIGHT);
                StackPane.setMargin(fundoSelo, new Insets(0, -8, -6, 0));
                moldura.getChildren().add(fundoSelo);
            });
        }

        VBox bloco = new VBox(8, moldura, nome);
        bloco.setAlignment(Pos.TOP_CENTER);
        bloco.getStyleClass().add("bloco-atalho");
        bloco.setUserData(a);
        Botoes.instalarDica(bloco, a.getTipo().rotulo + " · " + a.getAlvo() + abreNo);

        bloco.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                abrir(a, bloco);
            }
        });
        ContextMenu menu = menuDoBloco(a, bloco);
        bloco.setOnContextMenuRequested(e -> {
            menu.show(bloco, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return bloco;
    }

    private ContextMenu menuDoBloco(Atalho a, VBox bloco) {
        MenuItem abrir = new MenuItem("Abrir");
        abrir.setOnAction(e -> abrir(a, bloco));
        MenuItem editar = new MenuItem("Editar…");
        editar.setOnAction(e -> editarAtalho(a, a.getGrupoId()));
        MenuItem copiar = new MenuItem(a.getTipo() == TipoAtalho.SITE ? "Copiar o endereço" : "Copiar o caminho");
        copiar.setOnAction(e -> {
            AreaTransferencia.copiar(a.getAlvo());
            Aviso.sucesso("Copiado!");
        });

        ContextMenu menu = new ContextMenu(abrir, editar);
        if (a.getTipo() != TipoAtalho.SITE) {
            MenuItem naPasta = new MenuItem("Mostrar na pasta");
            naPasta.setOnAction(e -> mostrarNaPasta(a));
            menu.getItems().add(naPasta);
        }
        menu.getItems().add(copiar);

        Menu mover = new Menu("Mover para");
        for (GrupoAtalho g : grupos) {
            if (g.getId() == a.getGrupoId()) {
                continue;
            }
            MenuItem item = new MenuItem(g.getNome());
            item.setOnAction(e -> {
                servico.moverParaGrupo(a, g.getId());
                Aviso.sucesso("\"" + a.getNome() + "\" foi para " + g.getNome() + ".");
            });
            mover.getItems().add(item);
        }
        if (!mover.getItems().isEmpty()) {
            menu.getItems().add(mover);
        }

        MenuItem excluir = new MenuItem("Excluir");
        excluir.setOnAction(e -> excluirAtalho(a));
        menu.getItems().addAll(new SeparatorMenuItem(), excluir);
        return menu;
    }

    // ============================================================== abrir

    private void abrir(Atalho a, Node bloco) {
        // Retorno na hora: uma pasta de rede pode levar segundos para abrir,
        // e sem isso o clique parece não ter pegado.
        bloco.getStyleClass().add("abrindo");
        PauseTransition volta = new PauseTransition(Duration.millis(1200));
        volta.setOnFinished(e -> bloco.getStyleClass().remove("abrindo"));
        volta.play();

        Thread t = new Thread(() -> {
            try {
                servico.abrir(a);
            } catch (Exception e) {
                Log.aviso("Atalho não abriu (" + a.getAlvo() + "): " + e.getMessage());
                Platform.runLater(() -> Aviso.erro(e.getMessage() == null
                        ? "Não consegui abrir \"" + a.getNome() + "\"." : e.getMessage()));
            }
        }, "abrir-atalho");
        t.setDaemon(true);
        t.start();
    }

    private void abrirPrimeiroEncontrado() {
        for (Node secao : areaGrupos.getChildren()) {
            Node achado = secao.lookup(".bloco-atalho");
            if (achado != null && achado.getUserData() instanceof Atalho a) {
                abrir(a, achado);
                return;
            }
        }
    }

    private void mostrarNaPasta(Atalho a) {
        Path caminho = Path.of(a.getAlvo().replace("\"", "").trim());
        if (a.getTipo() == TipoAtalho.PASTA) {
            WindowsIntegracao.abrir(caminho);
        } else if (caminho.toFile().exists()) {
            WindowsIntegracao.mostrarNaPasta(caminho);
        } else {
            Aviso.atencao("Não achei " + caminho.getFileName() + ". Ele foi movido ou renomeado?");
        }
    }

    // ===================================================== soltar de fora

    /**
     * Aceitar arquivos, pastas e links arrastados do Explorador ou do
     * navegador. É filtro (e não tratador) para valer também em cima dos
     * blocos, que já tratam o arrasto interno de reordenar.
     */
    private void aceitarSoltar(Secao secao, GrupoAtalho g) {
        secao.addEventFilter(DragEvent.DRAG_OVER, e -> {
            if (vemDeFora(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
                if (!secao.getStyleClass().contains("alvo-soltar")) {
                    secao.getStyleClass().add("alvo-soltar");
                }
                e.consume();
            }
        });
        secao.addEventFilter(DragEvent.DRAG_EXITED_TARGET, e -> {
            if (e.getTarget() == secao) {
                secao.getStyleClass().remove("alvo-soltar");
            }
        });
        secao.addEventFilter(DragEvent.DRAG_DROPPED, e -> {
            Dragboard db = e.getDragboard();
            if (!vemDeFora(db)) {
                return;
            }
            secao.getStyleClass().remove("alvo-soltar");
            List<String> alvos = new ArrayList<>();
            if (db.hasFiles()) {
                db.getFiles().forEach(f -> alvos.add(f.getAbsolutePath()));
            } else if (db.hasUrl()) {
                alvos.add(db.getUrl());
            } else if (db.hasString()) {
                alvos.add(db.getString().trim());
            }
            e.setDropCompleted(!alvos.isEmpty());
            e.consume();
            criarDeFora(alvos, g);
        });
    }

    private static boolean vemDeFora(Dragboard db) {
        return db.hasFiles() || db.hasUrl()
                || (db.hasString() && AtalhoService.deduzirTipo(db.getString()) == TipoAtalho.SITE);
    }

    private void criarDeFora(List<String> alvos, GrupoAtalho g) {
        List<Atalho> criados = new ArrayList<>();
        for (String alvo : alvos) {
            if (alvo == null || alvo.isBlank()) {
                continue;
            }
            Atalho a = new Atalho();
            a.setGrupoId(g.getId());
            a.setAlvo(alvo);
            a.setTipo(AtalhoService.deduzirTipo(alvo));
            a.setNome(AtalhoService.nomeSugerido(alvo, a.getTipo()));
            criados.add(servico.salvar(a));
        }
        if (criados.isEmpty()) {
            return;
        }
        Aviso.sucesso(criados.size() == 1
                ? "Atalho \"" + criados.get(0).getNome() + "\" criado em " + g.getNome() + "!"
                : criados.size() + " atalhos criados em " + g.getNome() + "!");
        criados.forEach(this::completarIcone);
    }

    /**
     * Busca o ícone de verdade fora da tela e grava quando chegar. O atalho
     * já aparece com o símbolo do tipo e troca de desenho sozinho.
     */
    private void completarIcone(Atalho a) {
        if (a.getModoIcone() != Atalho.ModoIcone.AUTOMATICO || a.getIconePng() != null) {
            return;
        }
        Thread t = new Thread(() -> IconesAtalho.automatico(a.getTipo(), a.getAlvo()).ifPresent(png -> {
            a.setIconePng(png);
            Platform.runLater(() -> servico.salvar(a));
        }), "icone-atalho");
        t.setDaemon(true);
        t.start();
    }

    // =========================================================== cadastros

    private void editarAtalho(Atalho existente, long grupoId) {
        if (grupos.isEmpty()) {
            Aviso.atencao("Crie um grupo primeiro: todo atalho mora num grupo.");
            editarGrupo(null);
            return;
        }
        boolean novo = existente == null;
        new EditorAtalho(getScene().getWindow(), existente, grupos, grupoId).abrir().ifPresent(a -> {
            try {
                servico.salvar(a);
                Aviso.sucesso(novo ? "Atalho \"" + a.getNome() + "\" criado!" : "Atalho atualizado.");
                completarIcone(a);
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            }
        });
    }

    private void excluirAtalho(Atalho a) {
        if (Dialogos.confirmarExclusao(getScene().getWindow(), "o atalho \"" + a.getNome() + "\"",
                "Só o atalho sai da tela: o programa, a pasta ou o site continuam onde estão.")) {
            servico.excluir(a.getId());
            Aviso.sucesso("Atalho excluído.");
        }
    }

    private void editarGrupo(GrupoAtalho existente) {
        boolean novo = existente == null;
        Optional<GrupoAtalho> g = new EditorGrupo(getScene().getWindow(), existente).abrir();
        g.ifPresent(grupo -> {
            try {
                servico.salvarGrupo(grupo);
                Aviso.sucesso(novo ? "Grupo \"" + grupo.getNome() + "\" criado!" : "Grupo renomeado.");
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            }
        });
    }

    private void excluirGrupo(GrupoAtalho g) {
        int n = g.getAtalhos().size();
        String detalhe = n == 0 ? "O grupo está vazio."
                : "Os " + n + (n == 1 ? " atalho de dentro sai" : " atalhos de dentro saem")
                + " junto. Programas, pastas e sites continuam onde estão.";
        if (Dialogos.confirmarExclusao(getScene().getWindow(), "o grupo \"" + g.getNome() + "\"", detalhe)) {
            servico.excluirGrupo(g.getId());
            Aviso.sucesso("Grupo excluído.");
        }
    }
}
