package br.com.myapp.modules.notas;

import br.com.myapp.security.GeradorSenha;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.MedidorForca;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Gerador de senhas.
 *
 * Oferece dois formatos: a senha aleatória comum e a frase-senha. A frase é
 * mais longa, mas muito mais fácil de ditar por telefone ou digitar a partir
 * de um papel — situações corriqueiras no atendimento a cliente.
 */
public class DialogoGeradorSenha {

    private final Window dono;

    private final TextField campoResultado = new TextField();
    private final MedidorForca medidor = new MedidorForca();
    private final Label rotuloForca = new Label();

    private final Slider tamanho = new Slider(8, 48, 16);
    private final CheckBox minusculas = new CheckBox("a-z");
    private final CheckBox maiusculas = new CheckBox("A-Z");
    private final CheckBox digitos = new CheckBox("0-9");
    private final CheckBox simbolos = new CheckBox("!@#$");
    private final CheckBox semAmbiguos = new CheckBox("Evitar caracteres parecidos (l, 1, I, O, 0)");

    private final ToggleGroup formato = new ToggleGroup();
    private final RadioButton aleatoria = new RadioButton("Senha aleatória");
    private final RadioButton frase = new RadioButton("Frase-senha");

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

        dialogo.getDialogPane().setContent(montar());
        Dialogos.aplicarTema(dialogo.getDialogPane());
        dialogo.getDialogPane().lookupButton(usar).getStyleClass().add("botao-primario");

        gerar();

        dialogo.setResultConverter(botao -> botao == usar ? campoResultado.getText() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }

    private VBox montar() {
        // ---------- resultado ----------
        campoResultado.getStyleClass().addAll("campo", "campo-grande", "campo-codigo");
        campoResultado.setEditable(false);
        HBox.setHgrow(campoResultado, Priority.ALWAYS);

        Button regerar = Botoes.icone(Icone.Simbolo.GERAR, "Gerar outra");
        regerar.setOnAction(e -> gerar());

        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR, "Copiar");
        copiar.setOnAction(e -> AreaTransferencia.copiarSegredo(campoResultado.getText()));

        HBox linhaResultado = new HBox(8, campoResultado, regerar, copiar);
        linhaResultado.setAlignment(Pos.CENTER_LEFT);

        rotuloForca.getStyleClass().add("secao-dica");

        Secao secaoResultado = new Secao(Icone.Simbolo.CHAVE, "SENHA GERADA",
                linhaResultado, medidor, rotuloForca);

        // ---------- formato ----------
        aleatoria.setToggleGroup(formato);
        frase.setToggleGroup(formato);
        aleatoria.setSelected(true);
        formato.selectedToggleProperty().addListener((o, a, n) -> {
            atualizarDisponibilidade();
            gerar();
        });

        HBox linhaFormato = new HBox(18, aleatoria, frase);
        linhaFormato.setAlignment(Pos.CENTER_LEFT);

        // ---------- tamanho ----------
        tamanho.setShowTickMarks(true);
        tamanho.setShowTickLabels(true);
        tamanho.setMajorTickUnit(8);
        tamanho.setMinorTickCount(3);
        tamanho.setSnapToTicks(true);
        tamanho.setBlockIncrement(1);
        tamanho.valueProperty().addListener((o, a, n) -> gerar());

        Label valorTamanho = new Label();
        valorTamanho.getStyleClass().add("secao-campo");
        valorTamanho.textProperty().bind(
                tamanho.valueProperty().asString("%.0f caracteres"));

        HBox linhaTamanho = new HBox(14, tamanho, valorTamanho);
        HBox.setHgrow(tamanho, Priority.ALWAYS);
        linhaTamanho.setAlignment(Pos.CENTER_LEFT);

        // ---------- composição ----------
        for (CheckBox caixa : new CheckBox[]{minusculas, maiusculas, digitos, simbolos}) {
            caixa.setSelected(true);
            caixa.selectedProperty().addListener((o, a, n) -> gerar());
        }
        semAmbiguos.setSelected(true);
        semAmbiguos.selectedProperty().addListener((o, a, n) -> gerar());

        HBox grupos = new HBox(18, minusculas, maiusculas, digitos, simbolos);
        grupos.setAlignment(Pos.CENTER_LEFT);

        Secao secaoOpcoes = new Secao(Icone.Simbolo.CONFIGURACOES, "COMO GERAR",
                linhaFormato,
                Secao.comRotulo("Tamanho", linhaTamanho),
                Secao.comRotulo("Incluir", grupos),
                semAmbiguos,
                Secao.dica("Evitar parecidos ajuda quando a senha vai ser ditada "
                        + "por telefone ou copiada de um papel."));

        VBox forma = new VBox(18, secaoResultado, secaoOpcoes);
        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(520);
        return forma;
    }

    private void atualizarDisponibilidade() {
        boolean ehFrase = frase.isSelected();
        minusculas.setDisable(ehFrase);
        maiusculas.setDisable(ehFrase);
        digitos.setDisable(ehFrase);
        simbolos.setDisable(ehFrase);
        semAmbiguos.setDisable(ehFrase);
    }

    private void gerar() {
        String senha;
        if (frase.isSelected()) {
            // No modo frase, o controle de tamanho vira quantidade de palavras.
            int palavras = Math.max(3, (int) Math.round(tamanho.getValue() / 5.0));
            senha = GeradorSenha.gerarFrase(palavras, "-");
        } else {
            GeradorSenha.Opcoes opcoes = new GeradorSenha.Opcoes();
            opcoes.tamanho = (int) Math.round(tamanho.getValue());
            opcoes.minusculas = minusculas.isSelected();
            opcoes.maiusculas = maiusculas.isSelected();
            opcoes.digitos = digitos.isSelected();
            opcoes.simbolos = simbolos.isSelected();
            opcoes.evitarAmbiguos = semAmbiguos.isSelected();
            senha = GeradorSenha.gerar(opcoes);
        }
        campoResultado.setText(senha);
        avaliar(senha);
    }

    private void avaliar(String senha) {
        char[] copia = senha.toCharArray();
        int nota = SecurityService.forca(copia);
        java.util.Arrays.fill(copia, '\0');

        medidor.definir(nota);
        rotuloForca.setText(switch (nota) {
            case 0, 1 -> "Fraca — aumente o tamanho.";
            case 2 -> "Razoável.";
            case 3 -> "Boa senha.";
            default -> "Senha muito boa.";
        });
    }
}
