package br.com.myapp.ui;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * O cartão que agrupa várias {@link Opcao}, com um traço fino entre elas.
 *
 * <pre>
 *   ╭──────────────────────────────────────╮
 *   │  ⌂  Início                    (══●)  │
 *   │   └ ▢ Recados                 (══●)  │
 *   │──────────────────────────────────────│
 *   │  ▥  Kanban                    (●  )  │
 *   ╰──────────────────────────────────────╯
 * </pre>
 *
 * <p>A sub-opção não ganha traço antes dela: fica colada na de cima, que é
 * de quem ela faz parte. O traço é um nó entre as linhas, e não a borda de
 * baixo de cada uma, porque o CSS do JavaFX não tem {@code :last-child} para
 * tirar a borda da última.
 */
public class ListaOpcoes extends VBox {

    public ListaOpcoes(Opcao... opcoes) {
        getStyleClass().add("lista-opcoes");
        setMaxWidth(560);
        for (Opcao o : opcoes) {
            adicionar(o);
        }
    }

    public ListaOpcoes adicionar(Opcao opcao) {
        if (!getChildren().isEmpty() && !opcao.isSubOpcao()) {
            Region divisor = new Region();
            divisor.getStyleClass().add("divisor-opcao");
            VBox.setMargin(divisor, new Insets(0, 10, 0, 10));
            getChildren().add(divisor);
        }
        getChildren().add(opcao);
        return this;
    }
}
