package br.com.myapp.modules.uteis;

import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.util.StringConverter;

/**
 * Os controles da régua de leitura: ligar e desligar, altura, largura,
 * opacidade e cor. A faixa em si é a {@link Regua}.
 *
 * <p>Os controles se ligam às propriedades da régua nos dois sentidos: mexer
 * no controle muda a faixa na hora, e arrastar a borda da faixa move o
 * controle. O que ficou gravado (tamanho, cor, lugar) volta na próxima vez.
 */
public class PainelRegua extends Secao {

    private final Button alternar = Botoes.primario("Ligar a régua", Icone.Simbolo.REGUA);

    public PainelRegua() {
        super(Icone.Simbolo.REGUA, "RÉGUA DE LEITURA",
                "Uma faixa translúcida por cima de qualquer programa, para marcar a linha de um relatório.");

        Botoes.instalarDica(alternar, "Mostra a faixa sobre a tela. Esc, ou o botão direito sobre ela, desliga.");
        alternar.setOnAction(e -> Regua.alternar());
        Regua.ativa().addListener((o, antes, agora) -> mostrarEstado(agora));
        mostrarEstado(Regua.ativa().get());

        double larguraDaTela = Math.floor(Screen.getPrimary().getBounds().getWidth());

        Slider altura = slider(Regua.ALTURA_MIN, Regua.ALTURA_MAX);
        altura.valueProperty().bindBidirectional(Regua.altura());
        Slider largura = slider(Regua.LARGURA_MIN, Math.max(larguraDaTela, Regua.LARGURA_MIN + 1));
        largura.valueProperty().bindBidirectional(Regua.largura());
        Slider opacidade = slider(Regua.OPACIDADE_MIN, Regua.OPACIDADE_MAX);
        opacidade.valueProperty().bindBidirectional(Regua.opacidade());

        ComboBox<Regua.Cor> cor = new ComboBox<>();
        cor.getStyleClass().add("campo");
        cor.getItems().setAll(Regua.Cor.values());
        cor.setConverter(new StringConverter<>() {
            @Override
            public String toString(Regua.Cor c) {
                return c == null ? "" : c.nome();
            }

            @Override
            public Regua.Cor fromString(String texto) {
                return null;
            }
        });
        cor.valueProperty().bindBidirectional(Regua.cor());
        cor.setOnHidden(e -> Regua.guardar());

        HBox linhaBotao = new HBox(10, alternar);
        linhaBotao.setAlignment(Pos.CENTER_LEFT);

        com(linhaBotao,
                linha("Altura", altura, rotuloValor(altura, v -> Math.round(v) + " px")),
                linha("Largura", largura, rotuloValor(largura, v -> Math.round(v) + " px")),
                linha("Opacidade", opacidade, rotuloValor(opacidade, v -> Math.round(v * 100) + " %")),
                linha("Cor", cor, null),
                Secao.dica("Arraste a faixa para mover e as bordas para redimensionar. "
                        + "Esc, ou o botão direito sobre ela, desliga. Ela captura o clique só onde está; "
                        + "o resto da tela segue normal."));
    }

    private void mostrarEstado(boolean ligada) {
        alternar.setText(ligada ? "Desligar a régua" : "Ligar a régua");
    }

    /** Um controle deslizante que grava ao soltar, e não a cada pixel. */
    private static Slider slider(double minimo, double maximo) {
        Slider s = new Slider(minimo, maximo, minimo);
        s.setPrefWidth(260);
        s.setOnMouseReleased(e -> Regua.guardar());
        s.valueChangingProperty().addListener((o, antes, mudando) -> {
            if (!mudando) {
                Regua.guardar();
            }
        });
        return s;
    }

    private static Label rotuloValor(Slider slider, java.util.function.DoubleFunction<String> formato) {
        Label l = new Label(formato.apply(slider.getValue()));
        l.getStyleClass().add("texto-fraco");
        l.setMinWidth(60);
        slider.valueProperty().addListener((o, antes, agora) -> l.setText(formato.apply(agora.doubleValue())));
        return l;
    }

    private static HBox linha(String nome, javafx.scene.Node controle, Label valor) {
        Label rotulo = new Label(nome);
        rotulo.getStyleClass().add("secao-campo");
        rotulo.setMinWidth(90);
        HBox linha = valor == null
                ? new HBox(10, rotulo, controle)
                : new HBox(10, rotulo, controle, valor);
        linha.setAlignment(Pos.CENTER_LEFT);
        return linha;
    }
}
