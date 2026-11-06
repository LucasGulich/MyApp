package br.com.myapp.ui;

import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Uma opção liga/desliga: ícone, nome e a chave na ponta direita.
 *
 * <pre>
 *   ⌂  Início                                (══●)
 *    └ ▢ Recados                             (══●)   ← {@link #comoSubOpcao()}
 *   ↻  Parar de repetir em  [12/10/2026]     (══●)   ← {@link #comExtra}
 * </pre>
 *
 * <p>É o padrão do app para tudo que <b>liga ou desliga</b> — no lugar da caixa
 * de seleção. A linha inteira é clicável (menos os campos extras, que têm
 * vida própria), o nome esmaece quando desliga e a descrição vai no balão,
 * nunca em texto miúdo ao lado.
 *
 * <p>Fala a mesma língua da {@code CheckBox} ({@link #isSelected()},
 * {@link #selectedProperty()}, {@link #setText}) para a troca nas telas ser
 * direta. Várias opções juntas vão numa {@link ListaOpcoes}.
 */
public class Opcao extends HBox {

    private final Interruptor chave = new Interruptor();
    private final Label rotulo = new Label();
    private final HBox extras = new HBox(8);
    private final Icone.Simbolo simbolo;

    public Opcao(String texto) {
        this(null, texto);
    }

    public Opcao(Icone.Simbolo simbolo, String texto) {
        super(10);
        this.simbolo = simbolo;
        getStyleClass().add("linha-opcao");
        setAlignment(Pos.CENTER_LEFT);

        rotulo.setText(texto);
        rotulo.getStyleClass().add("nome-opcao");
        rotulo.setGraphicTextGap(10);
        rotulo.setWrapText(true);
        rotulo.setMinHeight(Region.USE_PREF_SIZE);
        if (simbolo != null) {
            rotulo.setGraphic(Icone.de(simbolo, 17));
        }

        extras.setAlignment(Pos.CENTER_LEFT);
        extras.setManaged(false);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        getChildren().addAll(rotulo, extras, espaco, chave);

        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!dentroDosExtras(e)) {
                chave.alternar();
            }
        });

        chave.ligadoProperty().addListener((obs, antes, agora) -> pintar());
        pintar();
    }

    /** O balão que diz o que a opção faz. */
    public Opcao comDescricao(String descricao) {
        if (descricao != null && !descricao.isBlank()) {
            Botoes.instalarDica(this, descricao);
        }
        return this;
    }

    /**
     * Campos que completam a opção, entre o nome e a chave — a data de
     * "parar de repetir em", a hora de "avisar às". Clicar neles não mexe na
     * chave; eles ficam desabilitados enquanto ela está desligada.
     */
    public Opcao comExtra(Node... nos) {
        extras.getChildren().addAll(nos);
        extras.setManaged(true);
        extras.disableProperty().bind(chave.ligadoProperty().not());
        return this;
    }

    /** Pendurada na opção de cima por um traço "└": é parte dela. */
    public Opcao comoSubOpcao() {
        Region ramo = new Region();
        ramo.getStyleClass().add("ramo-opcao");
        ramo.setMinSize(12, 14);
        ramo.setMaxSize(12, 14);
        ramo.setTranslateY(-6);
        getChildren().add(0, ramo);
        getStyleClass().add("sub-opcao");
        if (simbolo != null) {
            rotulo.setGraphic(Icone.de(simbolo, 15));
        }
        return this;
    }

    /**
     * Só vale com a outra ligada: desligada a de cima, esta esmaece e não
     * responde — mas guarda a escolha, que volta quando a de cima religar.
     * Ex.: as ferramentas do Úteis, com o Úteis fora do menu.
     */
    public Opcao dependeDe(Opcao pai) {
        disableProperty().bind(pai.selectedProperty().not());
        return this;
    }

    /** Sempre ligada: chave ligada, linha esmaecida, sem resposta ao clique. */
    public Opcao travar() {
        chave.travar();
        getStyleClass().add("travada");
        return this;
    }

    public boolean isSubOpcao() {
        return getStyleClass().contains("sub-opcao");
    }

    // ---------------------------------------------- como a CheckBox

    public boolean isSelected() {
        return chave.isLigado();
    }

    public void setSelected(boolean valor) {
        chave.setLigado(valor);
    }

    public BooleanProperty selectedProperty() {
        return chave.ligadoProperty();
    }

    public void setText(String texto) {
        rotulo.setText(texto);
    }

    public String getText() {
        return rotulo.getText();
    }

    // -------------------------------------------------------------- apoio

    private void pintar() {
        getStyleClass().remove("desligada");
        if (!chave.isLigado()) {
            getStyleClass().add("desligada");
        }
    }

    private boolean dentroDosExtras(MouseEvent e) {
        for (Node n = e.getPickResult().getIntersectedNode(); n != null; n = n.getParent()) {
            if (n == extras) {
                return true;
            }
            if (n == this) {
                return false;
            }
        }
        return false;
    }
}
