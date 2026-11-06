package br.com.myapp.modules.atalhos;

import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Criar ou renomear um grupo de atalhos: só o nome. */
public class EditorGrupo {

    private final Window dono;
    private final GrupoAtalho grupo;
    private final boolean ehNovo;

    public EditorGrupo(Window dono, GrupoAtalho existente) {
        this.dono = dono;
        this.ehNovo = existente == null;
        this.grupo = existente == null ? new GrupoAtalho() : existente;
    }

    public Optional<GrupoAtalho> abrir() {
        Dialog<GrupoAtalho> dialogo = new Dialog<>();
        dialogo.setTitle(ehNovo ? "Novo grupo" : "Renomear grupo");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        TextField nome = new TextField(grupo.getNome());
        nome.setPromptText("Ex.: Sistemas do cliente, Ferramentas de banco...");
        nome.getStyleClass().addAll("campo", "campo-grande");

        VBox forma = new VBox(new Secao(Icone.Simbolo.GRADE, "O GRUPO",
                Secao.comRotulo("Nome", nome),
                Secao.dica("Um grupo é uma faixa da tela: \"Aplicativos do dia a dia\", "
                        + "\"Pastas na rede\"...")));
        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(460);

        ButtonType salvar = new ButtonType(ehNovo ? "Criar grupo" : "Salvar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);
        dialogo.getDialogPane().setContent(forma);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");
        dialogo.getDialogPane().lookupButton(salvar).disableProperty()
                .bind(nome.textProperty().isEmpty());

        Platform.runLater(nome::requestFocus);
        dialogo.setResultConverter(b -> {
            if (b != salvar) {
                return null;
            }
            grupo.setNome(nome.getText());
            return grupo;
        });
        return dialogo.showAndWait();
    }
}
