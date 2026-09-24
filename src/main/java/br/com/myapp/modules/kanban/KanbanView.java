package br.com.myapp.modules.kanban;

import br.com.myapp.core.EventBus;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * O quadro Kanban.
 *
 * <pre>
 *   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
 *   │ A fazer   3 │ │ Em and.   1 │ │ Concluído 5 │
 *   ├─────────────┤ ├─────────────┤ ├─────────────┤
 *   │ ┌─────────┐ │ │ ┌─────────┐ │ │ ┌─────────┐ │
 *   │ │ cartão  │ │ │ │ cartão  │ │ │ │ cartão  │ │
 *   │ └─────────┘ │ │ └─────────┘ │ │ └─────────┘ │
 *   │ + cartão    │ │ + cartão    │ │ + cartão    │
 *   └─────────────┘ └─────────────┘ └─────────────┘
 * </pre>
 *
 * <h2>O arrasto</h2>
 *
 * São dois arrastos diferentes no mesmo quadro, e é isso que exige cuidado:
 *
 * <ul>
 *   <li><b>cartão</b> — começa em qualquer ponto do cartão, e pode cair sobre
 *       outro cartão (entra antes ou depois dele, conforme o lado) ou sobre a
 *       área livre de uma coluna (vai para o fim);</li>
 *   <li><b>coluna</b> — começa <i>só</i> pela alça do cabeçalho. Se a coluna
 *       inteira fosse arrastável, todo arrasto de cartão começaria também
 *       arrastando a coluna, porque o cartão está dentro dela.</li>
 * </ul>
 *
 * <p>Qual dos dois está em curso se sabe pelos campos estáticos abaixo: um
 * arrasto por vez, então não há o que confundir.
 */
public class KanbanView extends BorderPane {

    private static final DataFormat FORMATO_CARD = new DataFormat("myapp/kanban-card");
    private static final DataFormat FORMATO_COLUNA = new DataFormat("myapp/kanban-coluna");

    /** O cartão em movimento e de onde ele saiu. */
    private static CardKanban cardEmMovimento;
    private static ColunaKanban colunaDeOrigem;

    /** A coluna em movimento, quando o arrasto é de coluna. */
    private static ColunaKanban colunaEmMovimento;

    private final KanbanService servico = new KanbanService();

    private final HBox quadro = new HBox(16);

    /**
     * A rolagem do quadro. Com colunas, ela rola na horizontal e o quadro tem
     * a largura das colunas; vazia, ela ajusta o quadro à largura da tela,
     * para o aviso de quadro vazio ficar centralizado e não encostado à
     * esquerda.
     */
    private ScrollPane rolagem;
    private final Label contador = new Label();
    private final CheckBox mostrarArquivados = new CheckBox("Mostrar arquivados");

    /** Guardado entre desenhos para o quadro não se reorganizar sozinho. */
    private List<ColunaKanban> colunas = List.of();

    public KanbanView() {
        getStyleClass().add("conteudo");

        servico.criarColunasIniciaisSeVazio();

        setTop(montarCabecalho());
        setCenter(montarQuadro());

        EventBus.ouvir(KanbanService.QuadroMudou.class,
                e -> Platform.runLater(this::recarregar));

        recarregar();
    }

    // ------------------------------------------------------------- cabeçalho

    private VBox montarCabecalho() {
        Label titulo = new Label("Kanban");
        titulo.getStyleClass().add("titulo-tela");

        contador.getStyleClass().add("subtitulo");
        VBox textos = new VBox(2, titulo, contador);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        mostrarArquivados.setOnAction(e -> recarregar());
        Botoes.instalarDica(mostrarArquivados,
                "Cartões arquivados ficam escondidos. Ligue para revê-los.");

        Button novaColuna = Botoes.primario("Nova coluna", Icone.Simbolo.ADICIONAR);
        novaColuna.setOnAction(e -> abrirEditorDeColuna(null));

        HBox linha = new HBox(16, textos, espaco, mostrarArquivados, novaColuna);
        linha.setAlignment(Pos.CENTER_LEFT);

        VBox cabecalho = new VBox(linha);
        cabecalho.setPadding(new Insets(0, 0, 18, 0));
        return cabecalho;
    }

    private ScrollPane montarQuadro() {
        quadro.setAlignment(Pos.TOP_LEFT);
        quadro.setPadding(new Insets(2, 4, 12, 0));
        quadro.setFillHeight(true);

        rolagem = new ScrollPane(quadro);
        rolagem.setFitToHeight(true);
        rolagem.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("rolagem-quadro");
        return rolagem;
    }

    // ------------------------------------------------------------ recarregar

    public void recarregar() {
        colunas = servico.quadro();
        quadro.getChildren().clear();

        rolagem.setFitToWidth(colunas.isEmpty());
        if (colunas.isEmpty()) {
            EstadoVazio vazio = montarEstadoVazio();
            HBox.setHgrow(vazio, Priority.ALWAYS);
            quadro.getChildren().add(vazio);
            contador.setText("0 colunas");
            return;
        }

        boolean comArquivados = mostrarArquivados.isSelected();
        int cartoes = 0;
        int arquivados = 0;
        for (ColunaKanban coluna : colunas) {
            quadro.getChildren().add(montarColuna(coluna, comArquivados));
            cartoes += coluna.visiveis(false).size();
            arquivados += (int) coluna.quantosArquivados();
        }

        contador.setText(colunas.size() + " coluna(s)  •  " + cartoes + " cartão(ões) à vista"
                + (arquivados > 0 ? "  •  " + arquivados + " arquivado(s)" : ""));
    }

    private EstadoVazio montarEstadoVazio() {
        return new EstadoVazio(Icone.Simbolo.QUADRO, "O quadro ainda não tem colunas.")
                .comExplicacao("Uma coluna é uma etapa do seu fluxo: \"A fazer\", \"Em andamento\", "
                        + "\"Concluído\". Os cartões andam entre elas conforme o trabalho anda.")
                .comAcao("Criar a primeira coluna", () -> abrirEditorDeColuna(null));
    }

    // --------------------------------------------------------------- colunas

    private VBox montarColuna(ColunaKanban coluna, boolean comArquivados) {
        VBox caixa = new VBox();
        caixa.getStyleClass().add("coluna-kanban");

        VBox lista = new VBox(10);
        lista.getStyleClass().add("lista-cartoes");

        for (CardKanban card : coluna.visiveis(comArquivados)) {
            lista.getChildren().add(montarCartao(coluna, card));
        }

        if (!comArquivados && coluna.quantosArquivados() > 0) {
            lista.getChildren().add(marcaDeArquivados(coluna));
        }

        ScrollPane rolagem = new ScrollPane(lista);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("rolagem-coluna");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        caixa.getChildren().addAll(
                linhaDeCor(coluna),
                cabecalhoDaColuna(coluna, comArquivados, caixa),
                rolagem,
                botaoDeNovoCartao(coluna));

        // A coluna inteira recebe cartões e também responde ao arrasto de
        // coluna. Os dois casos convivem no mesmo nó porque é o mesmo alvo
        // para quem arrasta — ver ligarSoltarNaColuna.
        ligarSoltarNaColuna(caixa, lista, coluna);
        return caixa;
    }

    /** A linha colorida no topo, que identifica a coluna de longe. */
    private Region linhaDeCor(ColunaKanban coluna) {
        Region linha = new Region();
        linha.getStyleClass().add("coluna-kanban-cor");
        linha.setVisible(coluna.getCor().temCor());
        linha.setManaged(coluna.getCor().temCor());
        if (coluna.getCor().temCor()) {
            linha.setStyle("-fx-background-color: " + coluna.getCor().faixa() + ";");
        }
        return linha;
    }

    private HBox cabecalhoDaColuna(ColunaKanban coluna, boolean comArquivados, VBox caixa) {
        Label nome = new Label(coluna.getNome());
        nome.getStyleClass().add("coluna-kanban-nome");

        Label quantos = new Label(String.valueOf(coluna.visiveis(comArquivados).size()));
        quantos.getStyleClass().addAll("etiqueta", "coluna-kanban-contagem");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Button alca = Botoes.icone(Icone.Simbolo.ARRASTAR,
                "Arraste por aqui para mudar a coluna de lugar");
        alca.getStyleClass().addAll("botao-icone-miudo", "alca-coluna");

        Button adicionar = Botoes.icone(Icone.Simbolo.ADICIONAR, "Novo cartão nesta coluna");
        adicionar.getStyleClass().add("botao-icone-miudo");
        adicionar.setOnAction(e -> abrirEditorDeCard(coluna, null));

        Button editar = Botoes.icone(Icone.Simbolo.EDITAR, "Renomear ou recolorir a coluna");
        editar.getStyleClass().add("botao-icone-miudo");
        editar.setOnAction(e -> abrirEditorDeColuna(coluna));

        Button excluir = Botoes.iconePerigo(Icone.Simbolo.EXCLUIR,
                "Excluir a coluna e os cartões dela");
        excluir.getStyleClass().add("botao-icone-miudo");
        excluir.setOnAction(e -> confirmarExclusaoDaColuna(coluna));

        // A alça é o único ponto por onde a coluna se move: ver o cabeçalho
        // da classe.
        ligarAlcaDeColuna(alca, caixa, coluna);

        HBox linha = new HBox(6, nome, quantos, espaco, alca, adicionar, editar, excluir);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("coluna-kanban-cabecalho");
        return linha;
    }

    private Button botaoDeNovoCartao(ColunaKanban coluna) {
        Button b = Botoes.comum("Adicionar cartão", Icone.Simbolo.ADICIONAR);
        b.getStyleClass().add("botao-novo-cartao");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> abrirEditorDeCard(coluna, null));
        return b;
    }

    private Label marcaDeArquivados(ColunaKanban coluna) {
        long quantos = coluna.quantosArquivados();
        Label l = new Label(quantos + (quantos == 1 ? " arquivado" : " arquivados"));
        l.setGraphic(Icone.de(Icone.Simbolo.BAIXAR, 13));
        l.getStyleClass().add("marca-arquivados");
        Botoes.instalarDica(l, "Ligue \"Mostrar arquivados\" no topo para vê-los");
        return l;
    }

    // --------------------------------------------------------------- cartões

    private CartaoKanban montarCartao(ColunaKanban coluna, CardKanban card) {
        CartaoKanban no = new CartaoKanban(card,
                alvo -> abrirEditorDeCard(coluna, alvo),
                this::arquivar,
                this::duplicar,
                this::confirmarExclusaoDoCard);

        ligarArrastoDeCartao(no, coluna, card);
        return no;
    }

    // ==================================================== arrastar: cartões

    private void ligarArrastoDeCartao(CartaoKanban no, ColunaKanban coluna, CardKanban card) {
        no.setOnDragDetected(evento -> {
            // Cartão arquivado está fora do fluxo; movê-lo não significa nada.
            if (card.isArquivado()) {
                return;
            }
            Dragboard prancheta = no.startDragAndDrop(TransferMode.MOVE);

            SnapshotParameters transparente = new SnapshotParameters();
            transparente.setFill(Color.TRANSPARENT);
            prancheta.setDragView(no.snapshot(transparente, null));

            ClipboardContent conteudo = new ClipboardContent();
            conteudo.put(FORMATO_CARD, String.valueOf(card.getId()));
            prancheta.setContent(conteudo);

            cardEmMovimento = card;
            colunaDeOrigem = coluna;
            no.getStyleClass().add("arrastando");
            evento.consume();
        });

        no.setOnDragOver(evento -> {
            if (cardEmMovimento != null && !cardEmMovimento.getId().equals(card.getId())) {
                evento.acceptTransferModes(TransferMode.MOVE);
                marcar(no, metadeDeCima(no, evento) ? "alvo-antes" : "alvo-depois");
            }
            evento.consume();
        });

        no.setOnDragExited(evento -> {
            limparMarcas(no);
            evento.consume();
        });

        no.setOnDragDropped(evento -> {
            limparMarcas(no);
            if (cardEmMovimento == null || cardEmMovimento.getId().equals(card.getId())) {
                evento.setDropCompleted(false);
                evento.consume();
                return;
            }
            servico.moverCard(cardEmMovimento, colunaDeOrigem, coluna,
                    card, metadeDeCima(no, evento));
            evento.setDropCompleted(true);
            encerrarArrasto();
            evento.consume();
        });

        no.setOnDragDone(evento -> {
            no.getStyleClass().remove("arrastando");
            encerrarArrasto();
            evento.consume();
        });
    }

    /**
     * A coluna como alvo: recebe cartões e recebe colunas.
     *
     * <p>Os dois casos ficam no mesmo nó de propósito. Quem arrasta um cartão
     * para outra coluna mira <b>na coluna</b>, e não no cartão de baixo — na
     * primeira versão só a lista de cartões aceitava soltar, e como a lista
     * ocupa apenas a altura dos cartões que existem, o espaço vazio embaixo (o
     * alvo mais natural de todos) não aceitava nada. Ligando na coluna
     * inteira, qualquer ponto dela serve.
     *
     * <p>Quando o ponteiro está sobre um cartão, é o cartão que trata e
     * consome o evento — só assim ele consegue oferecer a posição exata,
     * antes ou depois dele. Aqui embaixo fica o caso geral: vai para o fim.
     */
    private void ligarSoltarNaColuna(VBox caixa, VBox lista, ColunaKanban coluna) {
        caixa.setOnDragOver(evento -> {
            if (colunaEmMovimento != null) {
                if (!colunaEmMovimento.getId().equals(coluna.getId())) {
                    evento.acceptTransferModes(TransferMode.MOVE);
                    marcar(caixa, metadeEsquerda(caixa, evento)
                            ? "alvo-antes-h" : "alvo-depois-h");
                }
            } else if (cardEmMovimento != null) {
                evento.acceptTransferModes(TransferMode.MOVE);
                marcar(lista, "recebendo");
            }
            evento.consume();
        });

        caixa.setOnDragExited(evento -> {
            limparMarcas(caixa);
            limparMarcas(lista);
            evento.consume();
        });

        caixa.setOnDragDropped(evento -> {
            limparMarcas(caixa);
            limparMarcas(lista);

            if (colunaEmMovimento != null) {
                boolean outra = !colunaEmMovimento.getId().equals(coluna.getId());
                if (outra) {
                    reposicionarColuna(colunaEmMovimento, coluna,
                            metadeEsquerda(caixa, evento));
                }
                evento.setDropCompleted(outra);
            } else if (cardEmMovimento != null) {
                servico.moverCard(cardEmMovimento, colunaDeOrigem, coluna, null, false);
                evento.setDropCompleted(true);
            } else {
                evento.setDropCompleted(false);
            }
            encerrarArrasto();
            evento.consume();
        });
    }

    /**
     * Zera o estado do arrasto.
     *
     * <p>Chamado ao soltar, e não só em {@code DRAG_DONE}: soltar dispara o
     * redesenho do quadro, o nó de origem deixa de existir e o evento de fim
     * pode nunca chegar a ele. Sem isto, o arrasto seguinte começaria achando
     * que o anterior ainda está em curso.
     */
    private static void encerrarArrasto() {
        cardEmMovimento = null;
        colunaDeOrigem = null;
        colunaEmMovimento = null;
    }

    // ==================================================== arrastar: colunas

    private void ligarAlcaDeColuna(Button alca, VBox caixa, ColunaKanban coluna) {
        alca.setOnDragDetected(evento -> {
            Dragboard prancheta = alca.startDragAndDrop(TransferMode.MOVE);

            SnapshotParameters transparente = new SnapshotParameters();
            transparente.setFill(Color.TRANSPARENT);
            prancheta.setDragView(caixa.snapshot(transparente, null));

            ClipboardContent conteudo = new ClipboardContent();
            conteudo.put(FORMATO_COLUNA, String.valueOf(coluna.getId()));
            prancheta.setContent(conteudo);

            colunaEmMovimento = coluna;
            caixa.getStyleClass().add("arrastando");
            evento.consume();
        });

        alca.setOnDragDone(evento -> {
            caixa.getStyleClass().remove("arrastando");
            encerrarArrasto();
            evento.consume();
        });
    }

    private void reposicionarColuna(ColunaKanban movida, ColunaKanban alvo, boolean antes) {
        List<ColunaKanban> nova = new java.util.ArrayList<>(colunas);
        nova.removeIf(c -> c.getId().equals(movida.getId()));

        int onde = nova.indexOf(alvo);
        if (onde < 0) {
            onde = nova.size();
        } else if (!antes) {
            onde++;
        }
        nova.add(Math.max(0, Math.min(onde, nova.size())), movida);
        servico.reordenarColunas(nova);
    }

    // ------------------------------------------------------- apoio do arrasto

    private boolean metadeDeCima(Node no, DragEvent evento) {
        return evento.getY() < no.getBoundsInLocal().getHeight() / 2;
    }

    private boolean metadeEsquerda(Node no, DragEvent evento) {
        return evento.getX() < no.getBoundsInLocal().getWidth() / 2;
    }

    private void marcar(Node no, String classe) {
        limparMarcas(no);
        no.getStyleClass().add(classe);
    }

    private void limparMarcas(Node no) {
        no.getStyleClass().removeAll(
                "alvo-antes", "alvo-depois", "alvo-antes-h", "alvo-depois-h", "recebendo");
    }

    // ----------------------------------------------------------------- ações

    private void abrirEditorDeColuna(ColunaKanban coluna) {
        boolean ehNova = coluna == null;
        new EditorColuna(getScene().getWindow(), coluna).abrir().ifPresent(preenchida -> {
            try {
                servico.salvarColuna(preenchida);
                Aviso.sucesso(ehNova ? "Coluna criada com sucesso!" : "Coluna atualizada.");
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            }
        });
    }

    private void confirmarExclusaoDaColuna(ColunaKanban coluna) {
        long quantos = coluna.getCards().size();
        String detalhe = quantos == 0
                ? "A coluna vai para a lixeira e pode ser restaurada."
                : "Os " + quantos + " cartão(ões) dela vão junto. "
                        + "Tudo continua guardado e pode ser restaurado.";

        if (Dialogos.confirmarExclusao(getScene().getWindow(),
                "a coluna \"" + coluna.getNome() + "\"", detalhe)) {
            servico.excluirColuna(coluna.getId());
            Aviso.sucesso("Coluna excluída.");
        }
    }

    private void abrirEditorDeCard(ColunaKanban coluna, CardKanban card) {
        boolean ehNovo = card == null;
        new EditorCard(getScene().getWindow(), coluna.getId(), card).abrir()
                .ifPresent(preenchido -> {
                    try {
                        servico.salvarCard(preenchido);
                        Aviso.sucesso(ehNovo
                                ? "Cartão criado com sucesso!"
                                : "Cartão atualizado.");
                    } catch (IllegalArgumentException e) {
                        Dialogos.erro(getScene().getWindow(),
                                "Não foi possível salvar", e.getMessage());
                    } catch (IllegalStateException e) {
                        Dialogos.erro(getScene().getWindow(),
                                "Conteúdo protegido", e.getMessage());
                    }
                });
    }

    private void arquivar(CardKanban card) {
        boolean arquivando = !card.isArquivado();
        servico.alternarArquivado(card);
        Aviso.info(arquivando
                ? "Cartão arquivado — continua no quadro, escondido."
                : "Cartão de volta à vista.");
    }

    private void duplicar(CardKanban card) {
        try {
            servico.duplicar(card);
            Aviso.sucesso("Cópia criada.");
        } catch (IllegalStateException e) {
            Dialogos.erro(getScene().getWindow(), "Conteúdo protegido", e.getMessage());
        }
    }

    private void confirmarExclusaoDoCard(CardKanban card) {
        String nome = card.isProtegido() && !SecurityService.estaDestrancado()
                ? "este cartão protegido"
                : "o cartão \"" + card.getTitulo() + "\"";

        if (Dialogos.confirmarExclusao(getScene().getWindow(), nome,
                "Ele sai do quadro, mas continua guardado no banco.")) {
            servico.excluirCard(card.getId());
            Aviso.sucesso("Cartão excluído.");
        }
    }

    /** Redesenha ao trancar ou destrancar: o conteúdo protegido muda. */
    public void aoMudarBloqueio() {
        recarregar();
    }
}
