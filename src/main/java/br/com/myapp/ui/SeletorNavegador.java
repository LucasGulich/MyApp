package br.com.myapp.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Optional;

/**
 * A lista "abrir com": a primeira opção (que não escolhe nenhum) e os
 * navegadores instalados, cada um com o seu ícone.
 *
 * <pre>
 *   [ 🌐 Navegador padrão do Windows ▾ ]
 *   [ (C) Google Chrome                ]
 *   [ (E) Microsoft Edge               ]
 * </pre>
 *
 * <p>Usado na preferência geral (Configurações → Sistema) e no atalho de site.
 * O texto da primeira opção muda conforme o lugar: no atalho, ela segue a
 * preferência do MyApp. Devolve o caminho do .exe; vazio é "nenhum escolhido".
 */
public class SeletorNavegador extends ComboBox<Navegadores.Navegador> {

    public SeletorNavegador(String rotuloSemEscolha) {
        getStyleClass().add("campo");
        setPrefWidth(340);
        getItems().add(new Navegadores.Navegador(rotuloSemEscolha, "", null));
        getItems().addAll(Navegadores.instalados());
        setCellFactory(lista -> new Celula());
        setButtonCell(new Celula());
        setValue(getItems().get(0));
    }

    /** Marca o gravado; se ele não está mais instalado, aparece assim mesmo, avisando. */
    public void selecionar(String exe) {
        if (exe == null || exe.isBlank()) {
            setValue(getItems().get(0));
            return;
        }
        Optional<Navegadores.Navegador> achado = getItems().stream()
                .filter(n -> n.exe().equalsIgnoreCase(exe)).findFirst();
        if (achado.isPresent()) {
            setValue(achado.get());
        } else {
            Navegadores.Navegador sumido = new Navegadores.Navegador(
                    new File(exe).getName() + " (não encontrado)", exe, null);
            getItems().add(sumido);
            setValue(sumido);
        }
    }

    /** O .exe escolhido; vazio quando ficou na primeira opção. */
    public String exe() {
        return getValue() == null ? "" : getValue().exe();
    }

    private static final class Celula extends ListCell<Navegadores.Navegador> {
        @Override
        protected void updateItem(Navegadores.Navegador n, boolean vazio) {
            super.updateItem(n, vazio);
            if (vazio || n == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(n.nome());
            if (n.icone() != null) {
                ImageView img = new ImageView(new Image(new ByteArrayInputStream(n.icone()), 36, 36, true, true));
                img.setFitWidth(18);
                img.setFitHeight(18);
                setGraphic(img);
            } else {
                setGraphic(Icone.de(n.ehPadrao() ? Icone.Simbolo.GLOBO : Icone.Simbolo.ATENCAO, 16));
            }
        }
    }
}
