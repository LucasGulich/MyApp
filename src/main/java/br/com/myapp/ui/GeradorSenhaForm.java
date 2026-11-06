package br.com.myapp.ui;

import br.com.myapp.security.GeradorSenha;
import br.com.myapp.security.SecurityService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;

/**
 * O gerador de senhas: os controles e a regra, sem dizer onde aparecem.
 *
 * <p>Usado em dois lugares — o diálogo "Gerar senha" das notas, que devolve a
 * senha para o campo, e o bloco do Úteis, que só gera e copia. Cada um monta
 * as duas partes ({@link #resultado()} e {@link #opcoes()}) do seu jeito.
 *
 * <pre>
 *   [ x7#Kq…9pZ                         ] [↻] [⧉]
 *   ▰▰▰▱  Boa senha.
 *   [Senha aleatória | Frase-senha]
 *   Tamanho  ───●──────  16 caracteres
 *   ╭ Letras minúsculas (a-z)        (══●) ╮
 *   │ ...                                  │
 *   ╰ Evitar caracteres parecidos    (══●) ╯
 * </pre>
 *
 * <p>Dois formatos: a senha aleatória comum e a frase-senha, mais longa, mas
 * muito mais fácil de ditar por telefone ou digitar a partir de um papel.
 */
public class GeradorSenhaForm {

    private final TextField campoResultado = new TextField();
    private final MedidorForca medidor = new MedidorForca();
    private final Label rotuloForca = new Label();

    private final Slider tamanho = new Slider(8, 48, 16);
    private final Label valorTamanho = new Label();
    private final Opcao minusculas = new Opcao("Letras minúsculas  (a-z)");
    private final Opcao maiusculas = new Opcao("Letras maiúsculas  (A-Z)");
    private final Opcao digitos = new Opcao("Números  (0-9)");
    private final Opcao simbolos = new Opcao("Símbolos  (!@#$)");
    private final Opcao semAmbiguos = new Opcao("Evitar caracteres parecidos  (l, 1, I, O, 0)")
            .comDescricao("Ajuda quando a senha vai ser ditada por telefone ou copiada de um papel.");

    private final ToggleGroup formato = new ToggleGroup();
    private final ToggleButton aleatoria = new ToggleButton("Senha aleatória");
    private final ToggleButton frase = new ToggleButton("Frase-senha");

    private final VBox resultado;
    private final VBox opcoes;

    public GeradorSenhaForm() {
        // ---------- resultado ----------
        campoResultado.getStyleClass().addAll("campo", "campo-grande", "campo-codigo");
        campoResultado.setEditable(false);
        HBox.setHgrow(campoResultado, Priority.ALWAYS);

        Button regerar = Botoes.icone(Icone.Simbolo.GERAR, "Gerar outra");
        regerar.setOnAction(e -> gerar());
        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR, "Copiar");
        copiar.setOnAction(e -> {
            int segundos = AreaTransferencia.copiarSegredo(campoResultado.getText());
            Aviso.sucesso("Senha copiada! Ela some da área de transferência em " + segundos + " s.");
        });

        HBox linhaResultado = new HBox(8, campoResultado, regerar, copiar);
        linhaResultado.setAlignment(Pos.CENTER_LEFT);
        rotuloForca.getStyleClass().add("secao-dica");
        resultado = new VBox(10, linhaResultado, medidor, rotuloForca);

        // ---------- formato ----------
        HBox seletorFormato = new HBox(0, aleatoria, frase);
        seletorFormato.getStyleClass().add("seletor-visao");
        seletorFormato.setMaxWidth(Region.USE_PREF_SIZE);
        aleatoria.setToggleGroup(formato);
        frase.setToggleGroup(formato);
        aleatoria.setSelected(true);
        formato.selectedToggleProperty().addListener((o, antes, agora) -> {
            // Num grupo, clicar no já escolhido deixaria nenhum.
            if (agora == null) {
                antes.setSelected(true);
                return;
            }
            atualizarDisponibilidade();
            gerar();
        });

        // ---------- tamanho ----------
        tamanho.setShowTickMarks(true);
        tamanho.setShowTickLabels(true);
        tamanho.setMajorTickUnit(8);
        tamanho.setMinorTickCount(3);
        tamanho.setSnapToTicks(true);
        tamanho.setBlockIncrement(1);
        tamanho.valueProperty().addListener((o, a, n) -> gerar());
        valorTamanho.getStyleClass().add("secao-campo");
        valorTamanho.setMinWidth(Region.USE_PREF_SIZE);
        HBox linhaTamanho = new HBox(14, tamanho, valorTamanho);
        HBox.setHgrow(tamanho, Priority.ALWAYS);
        linhaTamanho.setAlignment(Pos.CENTER_LEFT);

        // ---------- composição ----------
        for (Opcao caixa : new Opcao[]{minusculas, maiusculas, digitos, simbolos, semAmbiguos}) {
            caixa.setSelected(true);
            caixa.selectedProperty().addListener((o, a, n) -> gerar());
        }
        ListaOpcoes grupos = new ListaOpcoes(minusculas, maiusculas, digitos, simbolos, semAmbiguos);

        opcoes = new VBox(12, seletorFormato,
                Secao.comRotulo("Tamanho", linhaTamanho),
                Secao.comRotulo("Incluir", grupos));

        gerar();
    }

    /** A senha, o medidor de força e o texto dele. */
    public Node resultado() {
        return resultado;
    }

    /** Formato, tamanho e o que incluir. */
    public Node opcoes() {
        return opcoes;
    }

    /** A senha que está na tela agora. */
    public String senha() {
        return campoResultado.getText();
    }

    public final void gerar() {
        String senha;
        if (frase.isSelected()) {
            // No modo frase, o controle de tamanho vira quantidade de palavras.
            int palavras = palavrasDaFrase();
            valorTamanho.setText(palavras + " palavras");
            senha = GeradorSenha.gerarFrase(palavras, "-");
        } else {
            GeradorSenha.Opcoes o = new GeradorSenha.Opcoes();
            o.tamanho = (int) Math.round(tamanho.getValue());
            o.minusculas = minusculas.isSelected();
            o.maiusculas = maiusculas.isSelected();
            o.digitos = digitos.isSelected();
            o.simbolos = simbolos.isSelected();
            o.evitarAmbiguos = semAmbiguos.isSelected();
            valorTamanho.setText(o.tamanho + " caracteres");
            senha = GeradorSenha.gerar(o);
        }
        campoResultado.setText(senha);
        avaliar(senha);
    }

    private int palavrasDaFrase() {
        return Math.max(3, (int) Math.round(tamanho.getValue() / 5.0));
    }

    private void atualizarDisponibilidade() {
        boolean ehFrase = frase.isSelected();
        for (Opcao o : new Opcao[]{minusculas, maiusculas, digitos, simbolos, semAmbiguos}) {
            o.setDisable(ehFrase);
        }
    }

    private void avaliar(String senha) {
        char[] copia = senha.toCharArray();
        int nota = SecurityService.forca(copia);
        Arrays.fill(copia, '\0');

        medidor.definir(nota);
        rotuloForca.setText(switch (nota) {
            case 0, 1 -> "Fraca — aumente o tamanho.";
            case 2 -> "Razoável.";
            case 3 -> "Boa senha.";
            default -> "Senha muito boa.";
        });
    }
}
