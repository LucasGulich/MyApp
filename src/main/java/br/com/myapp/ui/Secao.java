package br.com.myapp.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Um bloco de formulário, com título próprio e moldura.
 *
 * Existe porque um formulário longo em uma coluna só vira uma parede de
 * campos: tudo parece pertencer a tudo, e o olho não encontra onde começa
 * cada assunto. Agrupando em blocos com respiro entre eles, a mesma
 * quantidade de campos passa a ser lida como uma sequência de tópicos.
 *
 * <pre>
 *   ┌─────────────────────────────────────┐
 *   │ ▢  O QUE É  ───────────────────────  │  ← título com ícone
 *   │                                     │
 *   │  [ campo ]                          │  ← conteúdo
 *   │  [ campo ]                          │
 *   └─────────────────────────────────────┘
 * </pre>
 */
public class Secao extends VBox {

    private final VBox conteudo = new VBox(10);

    /** A linha do título, onde as ações do bloco entram, à direita. */
    private HBox topo;

    public Secao(Icone.Simbolo icone, String titulo, Node... filhos) {
        this(icone, titulo, null, filhos);
    }

    /**
     * @param icone      símbolo do cabeçalho, ou nulo
     * @param titulo     título do bloco
     * @param explicacao linha de apoio abaixo do título, ou nulo
     */
    public Secao(Icone.Simbolo icone, String titulo, String explicacao, Node... filhos) {
        getStyleClass().add("secao");
        setSpacing(12);

        getChildren().add(montarCabecalho(icone, titulo, explicacao));

        conteudo.getStyleClass().add("secao-conteudo");
        conteudo.getChildren().addAll(filhos);
        getChildren().add(conteudo);
    }

    private VBox montarCabecalho(Icone.Simbolo icone, String titulo, String explicacao) {
        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("secao-titulo");
        if (icone != null) {
            rotulo.setGraphic(Icone.de(icone, 15));
        }

        Region linha = new Region();
        linha.getStyleClass().add("secao-linha");
        HBox.setHgrow(linha, Priority.ALWAYS);

        topo = new HBox(12, rotulo, linha);
        topo.setAlignment(Pos.CENTER_LEFT);

        VBox cabecalho = new VBox(4, topo);
        if (explicacao != null && !explicacao.isBlank()) {
            Label apoio = new Label(explicacao);
            apoio.getStyleClass().add("secao-explicacao");
            apoio.setWrapText(true);
            cabecalho.getChildren().add(apoio);
        }
        return cabecalho;
    }

    /** Acrescenta um nó ao corpo da seção. */
    public Secao com(Node... filhos) {
        conteudo.getChildren().addAll(filhos);
        return this;
    }

    /**
     * Põe ações no cabeçalho, depois da linha, no canto direito.
     *
     * <pre>
     *   ▢  CÓDIGO  ─────────────────────────  [⧉ Copiar tudo]
     * </pre>
     *
     * <p>Para o que age sobre este bloco e mais nada. Na fileira de botões do
     * alto da tela, "Copiar código" ficava longe do código e parecia valer
     * para a nota inteira.
     */
    public Secao comAcoes(Node... acoes) {
        topo.getChildren().addAll(acoes);
        return this;
    }

    /** O corpo, para quem precisar mexer nele depois de montado. */
    public VBox corpo() {
        return conteudo;
    }

    /** Mostra ou esconde a seção inteira, liberando o espaço quando oculta. */
    public void mostrar(boolean visivel) {
        setVisible(visivel);
        setManaged(visivel);
    }

    // ------------------------------------------------------------- apoio

    /** Rótulo de um campo dentro da seção. */
    public static Label campo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("secao-campo");
        return l;
    }

    /** Linha de explicação, em texto miúdo. */
    public static Label dica(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("secao-dica");
        l.setWrapText(true);
        l.setMaxWidth(560);
        return l;
    }

    /** Um campo com rótulo em cima, empilhados. */
    public static VBox comRotulo(String rotulo, Node campo) {
        VBox caixa = new VBox(6, campo(rotulo), campo);
        return caixa;
    }
}
