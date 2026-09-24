package br.com.myapp.modules.kanban;

import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Icone;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Um cartão desenhado no quadro.
 *
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │▍Conferir a fila de integração    │  ← risco da cor à esquerda
 *   │ Cliente X, fila parada desde…    │  ← descrição, recolhida em 3 linhas
 *   │ ⌄  📅 20/09          ✎  ▣  🗑   │  ← expandir · prazo · ações
 *   └──────────────────────────────────┘
 * </pre>
 *
 * <p>A descrição nasce recolhida em três linhas. Um cartão que cresce sem
 * limite empurra os outros para fora da tela, e a coluna deixa de ser uma
 * lista para virar um documento — quem quiser o texto inteiro clica na seta.
 */
public class CartaoKanban extends VBox {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    /** Quantas linhas da descrição aparecem antes de expandir. */
    private static final int LINHAS_RECOLHIDO = 4;

    private final CardKanban card;
    private final Label descricao = new Label();
    private final Button botaoExpandir = new Button();
    private boolean expandido;

    public CartaoKanban(CardKanban card,
                        Consumer<CardKanban> aoEditar,
                        Consumer<CardKanban> aoArquivar,
                        Consumer<CardKanban> aoDuplicar,
                        Consumer<CardKanban> aoExcluir) {
        this.card = card;

        getStyleClass().add("cartao-kanban");
        if (card.isArquivado()) {
            getStyleClass().add("arquivado");
        }
        if (card.getCor().temCor()) {
            setStyle("-fx-background-color: " + card.getCor().fundo() + ";");
        }
        setSpacing(6);

        HBox corpo = new HBox(10, risco(), textos());
        corpo.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(corpo, rodape(aoEditar, aoArquivar, aoDuplicar, aoExcluir));

        // Clicar duas vezes edita — o mesmo gesto dos recados e das notas.
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                aoEditar.accept(card);
            }
        });
    }

    /** O risco colorido na lateral, que só existe quando há cor escolhida. */
    private Region risco() {
        Region r = new Region();
        r.getStyleClass().add("faixa-cor");
        r.setVisible(card.getCor().temCor());
        r.setManaged(card.getCor().temCor());
        if (card.getCor().temCor()) {
            r.setStyle("-fx-background-color: " + card.getCor().faixa() + ";");
        }
        return r;
    }

    private VBox textos() {
        Label titulo = new Label(card.getTitulo());
        titulo.getStyleClass().add("cartao-kanban-titulo");
        titulo.setWrapText(true);

        VBox caixa = new VBox(4, titulo);
        HBox.setHgrow(caixa, Priority.ALWAYS);

        if (temDescricao()) {
            descricao.setText(card.getDescricao());
            descricao.getStyleClass().add("cartao-kanban-descricao");
            descricao.setWrapText(true);

            // Sem isto o VBox respeitaria a altura mínima do rótulo, que é a
            // altura do texto inteiro — e o corte em quatro linhas não
            // aconteceria.
            descricao.setMinHeight(0);

            // A largura só é conhecida depois do primeiro desenho, e é dela
            // que depende saber em quantas linhas o texto cabe. Por isso a
            // avaliação é refeita a cada mudança de largura, o que também
            // cobre o redimensionamento da janela.
            descricao.widthProperty().addListener((o, a, n) -> ajustarDescricao());
            caixa.getChildren().add(descricao);
        }
        return caixa;
    }

    private boolean temDescricao() {
        return card.getDescricao() != null && !card.getDescricao().isBlank();
    }

    private HBox rodape(Consumer<CardKanban> aoEditar,
                        Consumer<CardKanban> aoArquivar,
                        Consumer<CardKanban> aoDuplicar,
                        Consumer<CardKanban> aoExcluir) {

        HBox esquerda = new HBox(8);
        esquerda.setAlignment(Pos.CENTER_LEFT);

        if (temDescricao()) {
            esquerda.getChildren().add(prepararBotaoExpandir());
        }
        if (card.getPrazo() != null) {
            esquerda.getChildren().add(etiquetaDePrazo());
        }
        if (card.isProtegido()) {
            esquerda.getChildren().add(
                    Icone.de(Icone.Simbolo.CADEADO, 13, "icone-atencao"));
        }

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox acoes = new HBox(4,
                acao(Icone.Simbolo.EDITAR, "Editar este cartão", () -> aoEditar.accept(card)),
                acao(card.isArquivado() ? Icone.Simbolo.RESTAURAR : Icone.Simbolo.BAIXAR,
                        card.isArquivado()
                                ? "Desarquivar — volta para a vista"
                                : "Arquivar — sai da vista sem sair do quadro",
                        () -> aoArquivar.accept(card)),
                acao(Icone.Simbolo.DUPLICAR, "Duplicar este cartão",
                        () -> aoDuplicar.accept(card)),
                acaoPerigo(Icone.Simbolo.EXCLUIR,
                        "Excluir — vai para a lixeira e pode ser restaurado",
                        () -> aoExcluir.accept(card)));
        acoes.setAlignment(Pos.CENTER_RIGHT);
        acoes.getStyleClass().add("cartao-kanban-acoes");

        HBox linha = new HBox(6, esquerda, espaco, acoes);
        linha.setAlignment(Pos.CENTER_LEFT);
        return linha;
    }

    private Button prepararBotaoExpandir() {
        botaoExpandir.getStyleClass().add("botao-expandir");
        botaoExpandir.setGraphic(Icone.de(Icone.Simbolo.SETA_BAIXO, 14));
        Botoes.instalarDica(botaoExpandir, "Ver o texto inteiro");

        // Nasce escondido: só aparece se houver texto além das quatro linhas.
        // Um botão que promete mostrar mais e não mostra nada é pior do que
        // botão nenhum.
        botaoExpandir.setVisible(false);
        botaoExpandir.setManaged(false);

        botaoExpandir.setOnAction(e -> {
            expandido = !expandido;
            ajustarDescricao();
            botaoExpandir.setGraphic(Icone.de(expandido
                    ? Icone.Simbolo.SETA_CIMA : Icone.Simbolo.SETA_BAIXO, 14));
        });
        return botaoExpandir;
    }

    /**
     * Decide quantas linhas da descrição aparecem e se o botão tem razão de
     * existir.
     *
     * <p>Compara a altura que o texto <b>ocuparia inteiro</b> na largura atual
     * com o limite de quatro linhas. Se couber, não há o que expandir e o
     * botão fica fora — inclusive fora do layout, para não deixar um buraco no
     * rodapé do cartão.
     */
    private void ajustarDescricao() {
        double largura = descricao.getWidth();
        if (largura <= 0) {
            return;
        }
        double limite = alturaDeLinhas(LINHAS_RECOLHIDO);
        boolean transborda = descricao.prefHeight(largura) > limite + 0.5;

        botaoExpandir.setVisible(transborda);
        botaoExpandir.setManaged(transborda);

        descricao.setMaxHeight(expandido ? Double.MAX_VALUE : limite);
    }

    /**
     * Altura de N linhas na fonte que o tema deu ao rótulo.
     *
     * <p>Medida, e não fixada em pixels: a fonte vem do CSS e pode mudar com o
     * tema ou com a escala da tela, e um número cravado no código erraria o
     * corte justamente nas máquinas em que ele mais incomoda.
     */
    private double alturaDeLinhas(int quantas) {
        Text medida = new Text("Ag");
        medida.setFont(descricao.getFont());
        return medida.getLayoutBounds().getHeight() * quantas;
    }

    private Label etiquetaDePrazo() {
        Label l = new Label(DIA_MES.format(card.getPrazo()));
        l.setGraphic(Icone.de(Icone.Simbolo.CALENDARIO, 12));
        l.getStyleClass().addAll("etiqueta", "etiqueta-prazo");

        if (card.vencido()) {
            l.getStyleClass().add("prazo-vencido");
            Botoes.instalarDica(l, "Prazo vencido em " + formatoLongo());
        } else if (card.venceHoje()) {
            l.getStyleClass().add("prazo-hoje");
            Botoes.instalarDica(l, "O prazo é hoje");
        } else {
            Botoes.instalarDica(l, "Prazo: " + formatoLongo());
        }
        return l;
    }

    private String formatoLongo() {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(card.getPrazo());
    }

    private Button acao(Icone.Simbolo simbolo, String dica, Runnable aoClicar) {
        Button b = Botoes.icone(simbolo, dica);
        b.getStyleClass().add("botao-icone-miudo");
        b.setOnAction(e -> aoClicar.run());
        return b;
    }

    private Button acaoPerigo(Icone.Simbolo simbolo, String dica, Runnable aoClicar) {
        Button b = acao(simbolo, dica, aoClicar);
        b.getStyleClass().add("botao-icone-perigo");
        return b;
    }

    public CardKanban card() {
        return card;
    }

    /** O nó que representa este cartão, para quem precisa posicioná-lo. */
    public Node no() {
        return this;
    }
}
