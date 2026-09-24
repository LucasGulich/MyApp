package br.com.myapp.ui;

import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

/**
 * O campo de busca do aplicativo, igual em todas as telas.
 *
 * <pre>
 *   ┌──────────────────────────────────────┐
 *   │ Buscar nas notas...               ✕  │  ← o X só aparece com texto
 *   └──────────────────────────────────────┘
 * </pre>
 *
 * <p>Traz três comportamentos que toda busca deve ter e que, montados à mão
 * em cada tela, acabariam diferentes de uma para outra:
 * <ul>
 *   <li>o <b>X</b> no canto direito, que apaga o que foi digitado;</li>
 *   <li><b>Esc</b> faz o mesmo, sem tirar a mão do teclado;</li>
 *   <li><b>Ctrl+F</b>, de qualquer ponto da janela, traz o cursor para cá —
 *       ver {@link #focarNaTela(Scene)}.</li>
 * </ul>
 *
 * <p>A comparação em si (ignorar acentos e maiúsculas) não mora aqui, e sim
 * em {@link br.com.myapp.core.Texto}: quem filtra a lista é a tela, e o
 * serviço também busca sem tela nenhuma, como na busca global.
 */
public class CampoBusca extends StackPane {

    /** Classe de estilo que o Ctrl+F procura na cena. */
    private static final String CLASSE = "campo-busca";

    private final TextField campo = new TextField();
    private final Button limpar = new Button();

    public CampoBusca(String textoDeApoio) {
        getStyleClass().add(CLASSE);

        campo.setPromptText(textoDeApoio);
        campo.getStyleClass().add("campo");
        campo.setMaxWidth(Double.MAX_VALUE);

        limpar.getStyleClass().add("botao-limpar-busca");
        limpar.setGraphic(Icone.de(Icone.Simbolo.FECHAR, 12));
        // Fora da ordem do Tab: quem navega pelo teclado usa o Esc, e o Tab
        // deve ir do campo direto para o próximo controle da tela.
        limpar.setFocusTraversable(false);
        Botoes.instalarDica(limpar, "Limpar a busca (Esc)");
        limpar.setOnAction(e -> limparEFocar());

        limpar.visibleProperty().bind(campo.textProperty().isNotEmpty());
        StackPane.setAlignment(limpar, Pos.CENTER_RIGHT);
        StackPane.setMargin(limpar, new Insets(0, 7, 0, 0));

        campo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && !campo.getText().isEmpty()) {
                campo.clear();
                e.consume();
            }
        });

        getChildren().addAll(campo, limpar);
    }

    public StringProperty textProperty() {
        return campo.textProperty();
    }

    /** O texto digitado; nunca nulo. */
    public String getText() {
        return campo.getText() == null ? "" : campo.getText();
    }

    /** Põe o cursor no campo, com o texto já selecionado para ser trocado. */
    public void focar() {
        campo.requestFocus();
        campo.selectAll();
    }

    private void limparEFocar() {
        campo.clear();
        campo.requestFocus();
    }

    // ------------------------------------------------------------ Ctrl+F

    /**
     * Leva o cursor ao campo de busca da tela aberta, se ela tiver um.
     *
     * <p>Procura na cena inteira em vez de cada tela registrar o seu campo:
     * assim uma tela nova com busca já atende o Ctrl+F só por usar este
     * componente. Só conta campo que está de fato à vista — o Shell guarda as
     * telas dos outros módulos fora da cena, e uma coluna escondida por falta
     * de espaço não pode roubar o cursor.
     *
     * @return se encontrou um campo
     */
    public static boolean focarNaTela(Scene cena) {
        if (cena == null || cena.getRoot() == null) {
            return false;
        }
        for (Node no : cena.getRoot().lookupAll("." + CLASSE)) {
            if (no instanceof CampoBusca busca && estaAVista(busca)) {
                busca.focar();
                return true;
            }
        }
        return false;
    }

    private static boolean estaAVista(Node no) {
        if (no.isDisabled()) {
            return false;
        }
        for (Node atual = no; atual != null; atual = atual.getParent()) {
            if (!atual.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
