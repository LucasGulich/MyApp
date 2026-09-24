package br.com.myapp.ui;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Medidor de força da senha, em quatro segmentos.
 *
 * Preferido ao ProgressBar do JavaFX por dois motivos: o controle padrão traz
 * um estilo próprio que briga com o tema do aplicativo, e a leitura em blocos
 * comunica melhor a ideia de "níveis" do que uma barra contínua.
 */
public class MedidorForca extends HBox {

    private static final int SEGMENTOS = 4;

    private final Region[] blocos = new Region[SEGMENTOS];

    public MedidorForca() {
        super(4);
        setMaxWidth(340);
        setPrefWidth(340);

        for (int i = 0; i < SEGMENTOS; i++) {
            Region bloco = new Region();
            bloco.getStyleClass().add("segmento-forca");
            HBox.setHgrow(bloco, Priority.ALWAYS);
            bloco.setMaxWidth(Double.MAX_VALUE);
            blocos[i] = bloco;
            getChildren().add(bloco);
        }
        definir(0);
    }

    /**
     * Acende os blocos conforme a nota.
     *
     * @param nota de 0 a 4
     */
    public void definir(int nota) {
        String classeDaCor = switch (nota) {
            case 0 -> null;
            case 1 -> "fraca";
            case 2 -> "media";
            default -> "forte";
        };

        for (int i = 0; i < SEGMENTOS; i++) {
            blocos[i].getStyleClass().removeAll("fraca", "media", "forte");
            if (classeDaCor != null && i < nota) {
                blocos[i].getStyleClass().add(classeDaCor);
            }
        }
    }
}
