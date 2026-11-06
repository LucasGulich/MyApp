package br.com.myapp.ui;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;

/**
 * A grade de fichas para escolher um símbolo do catálogo ({@link Icone#ESCOLHAS}).
 *
 * <pre>
 *   [▢] [▤] [&lt;/&gt;] [⛁] [▥] [▭] ...
 * </pre>
 *
 * <p>Nasceu no cadastro de categoria das notas e virou peça comum quando os
 * atalhos precisaram do mesmo. Guarda e devolve a <b>chave</b> do catálogo
 * ("pasta", "servidor"...), que é o que vai para o banco.
 */
public class SeletorSimbolo extends FlowPane {

    private final ToggleGroup grupo = new ToggleGroup();

    public SeletorSimbolo() {
        super(8, 8);
        Icone.ESCOLHAS.forEach((chave, simbolo) -> {
            ToggleButton botao = new ToggleButton();
            botao.getStyleClass().add("chip-icone");
            botao.setGraphic(Icone.de(simbolo, 20));
            botao.setToggleGroup(grupo);
            botao.setUserData(chave);
            getChildren().add(botao);
        });
        // Num grupo, clicar no já escolhido deixaria nenhum.
        grupo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null && antes != null) {
                antes.setSelected(true);
            }
        });
    }

    /**
     * Marca o símbolo gravado. Aceita o que {@link Icone#porChave} aceita
     * (inclusive os emojis antigos das categorias); sem correspondência, o primeiro.
     */
    public void selecionar(String chave) {
        Icone.Simbolo atual = Icone.porChave(chave);
        grupo.getToggles().stream()
                .filter(t -> Icone.ESCOLHAS.get(String.valueOf(t.getUserData())) == atual)
                .findFirst()
                .ifPresentOrElse(t -> t.setSelected(true), () -> grupo.getToggles().get(0).setSelected(true));
    }

    /** A chave escolhida, ou nulo se nada foi marcado. */
    public String chave() {
        return grupo.getSelectedToggle() == null ? null : (String) grupo.getSelectedToggle().getUserData();
    }

    public ToggleGroup grupo() {
        return grupo;
    }
}
