package br.com.myapp.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * O que a tela mostra quando não há nada para mostrar.
 *
 * <pre>
 *                  ╭──────╮
 *                  │  ✎   │          ← ícone num círculo de acento
 *                  ╰──────╯
 *          Nenhum recado colado aqui.         ← o que aconteceu
 *     Recados são bilhetes rápidos, do tama-  ← por que importa (opcional)
 *     nho de um papel adesivo.
 *              [ +  Colar o primeiro recado ] ← o que fazer (opcional)
 * </pre>
 *
 * <p>Cada tela montava o seu, e havia seis jeitos diferentes: ícone de 48 ou
 * de 52 px, título em texto fraco ou forte, botão azul numa tela e cinza na
 * do lado — duas delas, lado a lado na tela inicial. Aqui a forma é uma só,
 * e a tela só diz o conteúdo.
 *
 * <p><b>O botão é sempre o comum, nunca o primário.</b> O azul cheio é da
 * ação principal da tela, e ela já está no cabeçalho ("Novo lembrete",
 * "Nova nota"). Duas chamadas azuis para a mesma coisa brigariam pelo olho —
 * e na tela inicial seriam duas ações principais diferentes.
 *
 * <p>Ocupa toda a largura que receber e centraliza o conteúdo dentro dela.
 * Não serve colocá-lo dentro de um {@code FlowPane}, que dá a cada filho só o
 * tamanho preferido e o encosta à esquerda.
 */
public class EstadoVazio extends VBox {

    private final Label explicacao = new Label();

    public EstadoVazio(Icone.Simbolo simbolo, String titulo) {
        getStyleClass().add("estado-vazio");
        setAlignment(Pos.CENTER);
        setMaxWidth(Double.MAX_VALUE);

        StackPane selo = new StackPane(Icone.de(simbolo, 26));
        selo.getStyleClass().add("estado-vazio-selo");

        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("estado-vazio-titulo");
        rotulo.setWrapText(true);
        rotulo.setTextAlignment(TextAlignment.CENTER);
        rotulo.setAlignment(Pos.CENTER);
        VBox.setMargin(rotulo, new Insets(16, 0, 0, 0));

        explicacao.getStyleClass().add("estado-vazio-explicacao");
        explicacao.setWrapText(true);
        // Sem isto, a frase que cabe numa linha só encosta à esquerda da
        // caixa: o alinhamento de texto só vale a partir da segunda linha.
        explicacao.setAlignment(Pos.CENTER);
        explicacao.setTextAlignment(TextAlignment.CENTER);
        explicacao.setMaxWidth(430);
        explicacao.setVisible(false);
        explicacao.setManaged(false);
        VBox.setMargin(explicacao, new Insets(6, 0, 0, 0));

        getChildren().addAll(selo, rotulo, explicacao);
    }

    /** Uma ou duas frases dizendo para que serve o que ainda não existe. */
    public EstadoVazio comExplicacao(String texto) {
        explicacao.setText(texto);
        explicacao.setVisible(true);
        explicacao.setManaged(true);
        return this;
    }

    /** O botão que resolve o vazio — em geral, criar o primeiro item. */
    public EstadoVazio comAcao(String texto, Runnable aoClicar) {
        Button botao = Botoes.comum(texto, Icone.Simbolo.ADICIONAR);
        botao.setOnAction(e -> aoClicar.run());
        VBox.setMargin(botao, new Insets(18, 0, 0, 0));
        getChildren().add(botao);
        return this;
    }
}
