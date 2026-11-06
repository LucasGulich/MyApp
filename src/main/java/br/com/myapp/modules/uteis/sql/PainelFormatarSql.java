package br.com.myapp.modules.uteis.sql;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.modules.uteis.sql.FormatadorSql.Banco;
import br.com.myapp.modules.uteis.sql.FormatadorSql.Caixa;
import br.com.myapp.modules.uteis.sql.FormatadorSql.Resultado;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.DestacadorSintaxe;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import br.com.myapp.ui.TextoSelecionavel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formatar SQL: colar a consulta grudada e recebê-la em linhas, colorida.
 *
 * <pre>
 *   { } FORMATAR SQL ──────────────────────────────────────────────
 *   Consulta        [ select c.id,c.nome from cliente c where …    ]
 *   Valores dos ?   [ opcional — do log do Hibernate vêm sozinhos  ]
 *   [PostgreSQL ▾]  [MAIÚSCULAS | minúsculas | Como veio]  [4 espaços ▾]
 *   [Formatar] [Compactar numa linha] [Limpar]
 *   RESULTADO ────────────────────────────────────────── [⧉ Copiar]
 *   SELECT
 *       c.id, …
 * </pre>
 *
 * <p>Colar o trecho inteiro do log do Hibernate (a consulta e as linhas
 * "binding parameter") já preenche os {@code ?}. Regras em {@link FormatadorSql}.
 */
public class PainelFormatarSql extends Secao {

    private static final Map<String, String> RECUOS = new LinkedHashMap<>();

    static {
        RECUOS.put("    ", "4 espaços");
        RECUOS.put("  ", "2 espaços");
        RECUOS.put("\t", "Tab");
    }

    private final Config config = Config.get();

    private final TextArea campoConsulta = new TextArea();
    private final TextArea campoValores = new TextArea();
    private final ComboBox<Banco> campoBanco = new ComboBox<>();
    private final ToggleGroup grupoCaixa = new ToggleGroup();
    private final ComboBox<String> campoRecuo = new ComboBox<>();

    private final VBox areaResultado = new VBox(8);
    private final VBox blocoResultado = new VBox();
    private final Label estado = new Label();
    private String resultadoAtual = "";
    /** O que foi feito por último — mudar uma opção refaz o mesmo. */
    private boolean ultimoFoiCompactar;

    public PainelFormatarSql() {
        super(Icone.Simbolo.CODIGO, "FORMATAR SQL",
                "Cole a consulta grudada — ou o trecho do log com os \"binding parameter\" — "
                        + "e receba em linhas, colorida e pronta para o DBeaver.");

        campoConsulta.getStyleClass().addAll("campo", "campo-codigo");
        campoConsulta.setPromptText("select c.id,c.nome from cliente c inner join pedido p on p.cliente_id=c.id …");
        campoConsulta.setPrefRowCount(6);
        campoConsulta.setWrapText(true);
        // Ctrl+Enter formata sem tirar a mão do teclado.
        campoConsulta.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) {
                executar(false);
                e.consume();
            }
        });

        campoValores.getStyleClass().addAll("campo", "campo-codigo");
        campoValores.setPromptText("Opcional. Um por linha, ou separados por vírgula: 1532, ABERTO, 2026-10-01");
        campoValores.setPrefRowCount(2);
        VBox blocoValores = Secao.comRotulo("Valores dos ?", campoValores);
        blocoValores.getChildren().add(Secao.dica("Número, true/false e null vão como estão; o resto ganha aspas. "
                + "Colando o log do Hibernate inteiro na consulta, os valores vêm sozinhos e este campo fica vazio."));

        // ---------- opções ----------
        campoBanco.getItems().setAll(Banco.values());
        campoBanco.getStyleClass().add("campo");
        campoBanco.setConverter(conversor(b -> b.rotulo));

        HBox seletorCaixa = new HBox(0);
        seletorCaixa.getStyleClass().add("seletor-visao");
        seletorCaixa.setMaxWidth(Region.USE_PREF_SIZE);
        for (Caixa c : Caixa.values()) {
            ToggleButton b = new ToggleButton(switch (c) {
                case MAIUSCULAS -> "MAIÚSCULAS";
                case MINUSCULAS -> "minúsculas";
                case COMO_VEIO -> "Como veio";
            });
            b.setUserData(c);
            b.setToggleGroup(grupoCaixa);
            seletorCaixa.getChildren().add(b);
        }
        Botoes.instalarDica(seletorCaixa, "Só as palavras do SQL (SELECT, FROM, WHERE…). "
                + "Nomes de tabela e coluna, e o que está entre aspas, nunca mudam.");

        campoRecuo.getItems().setAll(RECUOS.keySet());
        campoRecuo.getStyleClass().add("campo");
        campoRecuo.setConverter(conversor(RECUOS::get));

        FlowPane linhaOpcoes = new FlowPane(16, 10,
                comRotuloAoLado("Banco", campoBanco),
                comRotuloAoLado("Palavras-chave", seletorCaixa),
                comRotuloAoLado("Recuo", campoRecuo));
        linhaOpcoes.setAlignment(Pos.CENTER_LEFT);

        // ---------- ações ----------
        Button formatar = Botoes.primario("Formatar", Icone.Simbolo.CODIGO);
        formatar.setOnAction(e -> executar(false));
        Botoes.instalarDica(formatar, "Ctrl+Enter na consulta faz o mesmo.");
        Button compactar = Botoes.comum("Compactar numa linha", Icone.Simbolo.TEXTO);
        compactar.setOnAction(e -> executar(true));
        Botoes.instalarDica(compactar, "Tudo numa linha só, para colar em código, chamado ou script.");
        Button limpar = Botoes.comum("Limpar", Icone.Simbolo.FECHAR);
        limpar.setOnAction(e -> limpar());
        HBox linhaAcoes = new HBox(10, formatar, compactar, limpar);
        linhaAcoes.setAlignment(Pos.CENTER_LEFT);

        // ---------- resultado ----------
        Button copiar = Botoes.miudo("Copiar", Icone.Simbolo.COPIAR);
        copiar.setOnAction(e -> {
            AreaTransferencia.copiar(resultadoAtual);
            Aviso.sucesso("SQL copiado!");
        });
        Label rotuloResultado = new Label("RESULTADO");
        rotuloResultado.getStyleClass().add("secao-campo");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox topoResultado = new HBox(10, rotuloResultado, espaco, copiar);
        topoResultado.setAlignment(Pos.CENTER_LEFT);
        estado.getStyleClass().add("texto-fraco");
        estado.setWrapText(true);
        estado.setMinHeight(Region.USE_PREF_SIZE);
        areaResultado.getChildren().addAll(topoResultado, blocoResultado, estado);
        mostrar(areaResultado, false);

        com(Secao.comRotulo("Consulta", campoConsulta), blocoValores, linhaOpcoes, linhaAcoes, areaResultado);

        carregarPreferencias();
        campoBanco.valueProperty().addListener((o, a, n) -> refazer());
        grupoCaixa.selectedToggleProperty().addListener((o, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true);
            } else {
                refazer();
            }
        });
        campoRecuo.valueProperty().addListener((o, a, n) -> refazer());
    }

    // ============================================================ ações

    private void executar(boolean compactar) {
        String consulta = campoConsulta.getText();
        if (consulta == null || consulta.isBlank()) {
            Aviso.atencao("Cole uma consulta primeiro.");
            campoConsulta.requestFocus();
            return;
        }
        ultimoFoiCompactar = compactar;
        gravarPreferencias();
        try {
            Resultado r = compactar
                    ? FormatadorSql.compactar(consulta, campoValores.getText(), banco(), caixa())
                    : FormatadorSql.formatar(consulta, campoValores.getText(), banco(), caixa(), campoRecuo.getValue());
            mostrarResultado(r);
        } catch (Exception e) {
            Log.aviso("Não consegui formatar o SQL: " + e.getMessage());
            Aviso.erro("Não consegui formatar esta consulta. Confira se ela está completa.");
        }
    }

    /** Mudou uma opção com resultado na tela: refaz do mesmo jeito. */
    private void refazer() {
        if (areaResultado.isVisible()) {
            executar(ultimoFoiCompactar);
        }
    }

    private void mostrarResultado(Resultado r) {
        resultadoAtual = r.sql();
        TextoSelecionavel codigo = new TextoSelecionavel(DestacadorSintaxe.destacar(r.sql(), "SQL"), r.sql());
        codigo.getStyleClass().add("bloco-codigo");
        blocoResultado.getChildren().setAll(codigo);

        StringBuilder s = new StringBuilder();
        if (r.preenchidos() > 0) {
            s.append(r.preenchidos()).append(r.preenchidos() == 1 ? " parâmetro preenchido." : " parâmetros preenchidos.");
        }
        if (r.semValor() > 0) {
            s.append(s.length() > 0 ? " " : "").append(r.semValor())
                    .append(r.semValor() == 1 ? " ? ficou sem valor." : " ? ficaram sem valor.");
        }
        if (r.valoresSobrando() > 0) {
            s.append(s.length() > 0 ? " " : "").append(r.valoresSobrando())
                    .append(r.valoresSobrando() == 1 ? " valor sobrou" : " valores sobraram")
                    .append(" — há mais valores que ?.");
        }
        boolean atencao = r.semValor() > 0 || r.valoresSobrando() > 0;
        estado.setText(s.toString());
        estado.setGraphic(s.length() == 0 ? null : Icone.de(atencao ? Icone.Simbolo.ATENCAO : Icone.Simbolo.SUCESSO,
                14, atencao ? "icone-atencao" : "icone-sucesso"));
        mostrar(estado, s.length() > 0);
        mostrar(areaResultado, true);
    }

    private void limpar() {
        campoConsulta.clear();
        campoValores.clear();
        resultadoAtual = "";
        blocoResultado.getChildren().clear();
        mostrar(areaResultado, false);
        campoConsulta.requestFocus();
    }

    // ====================================================== preferências

    private void carregarPreferencias() {
        try {
            campoBanco.setValue(Banco.valueOf(config.sqlBanco));
        } catch (Exception e) {
            campoBanco.setValue(Banco.POSTGRESQL);
        }
        Caixa caixa;
        try {
            caixa = Caixa.valueOf(config.sqlCaixa);
        } catch (Exception e) {
            caixa = Caixa.MAIUSCULAS;
        }
        Caixa escolhida = caixa;
        grupoCaixa.getToggles().stream().filter(t -> t.getUserData() == escolhida).findFirst()
                .ifPresent(t -> t.setSelected(true));
        campoRecuo.setValue(RECUOS.containsKey(config.sqlRecuo) ? config.sqlRecuo : "    ");
    }

    private void gravarPreferencias() {
        config.sqlBanco = banco().name();
        config.sqlCaixa = caixa().name();
        config.sqlRecuo = campoRecuo.getValue();
        config.salvar();
    }

    // ============================================================ apoio

    private Banco banco() {
        return campoBanco.getValue() == null ? Banco.POSTGRESQL : campoBanco.getValue();
    }

    private Caixa caixa() {
        return grupoCaixa.getSelectedToggle() == null ? Caixa.MAIUSCULAS : (Caixa) grupoCaixa.getSelectedToggle().getUserData();
    }

    private static HBox comRotuloAoLado(String rotulo, javafx.scene.Node campo) {
        Label l = new Label(rotulo);
        l.getStyleClass().add("secao-campo");
        HBox h = new HBox(8, l, campo);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static void mostrar(javafx.scene.Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }

    private static <T> StringConverter<T> conversor(java.util.function.Function<T, String> nome) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? "" : nome.apply(valor);
            }

            @Override
            public T fromString(String texto) {
                return null;
            }
        };
    }
}
