package br.com.myapp.modules.notas;

import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.GeradorSenhaForm;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * O gerador de senhas aberto a partir do campo de senha de uma nota: gera e
 * devolve a senha escolhida para o campo.
 *
 * <p>Os controles e a regra estão em {@link GeradorSenhaForm}, o mesmo que o
 * bloco "Gerador de senha" do Úteis usa.
 */
public class DialogoGeradorSenha {

    private final Window dono;
    private final GeradorSenhaForm gerador = new GeradorSenhaForm();

    public DialogoGeradorSenha(Window dono) {
        this.dono = dono;
    }

    /** Abre o gerador. Devolve a senha escolhida, ou vazio se cancelou. */
    public Optional<String> abrir() {
        Dialog<String> dialogo = new Dialog<>();
        dialogo.setTitle("Gerar senha");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        ButtonType usar = new ButtonType("Usar esta senha", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, usar);

        VBox forma = new VBox(18,
                new Secao(Icone.Simbolo.CHAVE, "SENHA GERADA", gerador.resultado()),
                new Secao(Icone.Simbolo.CONFIGURACOES, "COMO GERAR", gerador.opcoes()));
        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(520);

        dialogo.getDialogPane().setContent(Dialogos.rolavel(forma, 540, 640));
        Dialogos.aplicarTema(dialogo.getDialogPane());
        dialogo.getDialogPane().lookupButton(usar).getStyleClass().add("botao-primario");

        dialogo.setResultConverter(botao -> botao == usar ? gerador.senha() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }
}
