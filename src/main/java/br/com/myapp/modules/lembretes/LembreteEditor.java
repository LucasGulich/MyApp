package br.com.myapp.modules.lembretes;

import br.com.myapp.core.Config;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Janela de cadastro e edição de um lembrete.
 *
 * Os campos aparecem conforme o tipo de repetição escolhido: dias da semana só
 * surgem em "dias escolhidos", o dia do mês só em "todo mês", e assim por
 * diante. E o mesmo caminho do Lembrete.bat (descrição, data, hora), com as
 * opções que la não cabiam.
 */
public class LembreteEditor {

    /** Atalhos de antecedência oferecidos como botoes. */
    private static final Map<String, Integer> ATALHOS_DE_AVISO = new LinkedHashMap<>();

    static {
        ATALHOS_DE_AVISO.put("Na hora", 0);
        ATALHOS_DE_AVISO.put("5 min", 5);
        ATALHOS_DE_AVISO.put("15 min", 15);
        ATALHOS_DE_AVISO.put("30 min", 30);
        ATALHOS_DE_AVISO.put("1 h", 60);
        ATALHOS_DE_AVISO.put("2 h", 120);
        ATALHOS_DE_AVISO.put("1 dia", 1440);
        ATALHOS_DE_AVISO.put("1 semana", 10080);
    }

    private final Window dono;
    private final Lembrete lembrete;
    private final boolean ehNovo;

    // --- campos ---
    private final TextField campoTitulo = new TextField();
    private final TextArea campoDescricao = new TextArea();
    private final CheckBox campoSensivel = new CheckBox("Proteger este lembrete com a senha mestra");
    private final ComboBox<TipoRecorrencia> campoTipo = new ComboBox<>();
    private final DatePicker campoData = new DatePicker();
    private final TextField campoHora = new TextField();
    private final CheckBox campoTemFim = new CheckBox("Parar de repetir em");
    private final DatePicker campoFim = new DatePicker();
    private final Spinner<Integer> campoDiaMes = new Spinner<>(1, 31, 1);
    private final Spinner<Integer> campoIntervalo = new Spinner<>(1, 10080, 30);
    private final CheckBox campoSom = new CheckBox("Tocar som quando este lembrete alertar");
    private final ColorPicker campoCor = new ColorPicker();
    private final TextField campoAcao = new TextField();

    private final Map<DayOfWeek, ToggleButton> botoesDia = new LinkedHashMap<>();
    private final Map<Integer, ToggleButton> botoesAviso = new LinkedHashMap<>();
    private final TextField campoAvisoCustom = new TextField();

    // --- blocos que aparecem e somem ---
    private final VBox blocoDiasSemana = new VBox(8);
    private final VBox blocoDiaMes = new VBox(8);
    private final VBox blocoIntervalo = new VBox(8);
    private final VBox blocoFim = new VBox(8);

    public LembreteEditor(Window dono, Lembrete existente) {
        this.dono = dono;
        this.ehNovo = existente == null;
        this.lembrete = existente == null ? new Lembrete() : existente;
    }

    /**
     * Abre a janela e devolve o lembrete preenchido, ou vazio se o usuário
     * cancelou. A gravação fica por conta de quem chamou.
     */
    public Optional<Lembrete> abrir() {
        Dialog<Lembrete> dialogo = new Dialog<>();
        dialogo.setTitle(ehNovo ? "Novo lembrete" : "Editar lembrete");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);

        ButtonType salvar = new ButtonType(ehNovo ? "Criar lembrete" : "Salvar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);

        ScrollPane rolagem = new ScrollPane(montarFormulario());
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem-formulario");
        // Mais largo que antes: as seções precisam de espaço para respirar,
        // e vários campos passam a caber lado a lado em vez de empilhados.
        rolagem.setPrefViewportWidth(640);
        rolagem.setPrefViewportHeight(620);
        dialogo.getDialogPane().setContent(rolagem);

        Dialogos.aplicarTema(dialogo.getDialogPane());

        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");

        carregarValores();

        dialogo.setResultConverter(botao -> botao == salvar ? coletarValores() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }

    // ------------------------------------------------------------ Formulário

    /**
     * Monta o formulário em blocos.
     *
     * Cada assunto vira uma {@link Secao} com título e moldura própria. Em uma
     * coluna só, os mesmos campos viravam uma parede em que tudo parecia
     * pertencer a tudo; separados, o formulário se lê como uma sequência de
     * tópicos.
     */
    private VBox montarFormulario() {
        VBox forma = new VBox(18);
        forma.getStyleClass().add("formulario");
        if ("claro".equals(Config.get().tema)) {
            forma.getStyleClass().add("claro");
        }

        forma.getChildren().addAll(
                secaoOQueE(),
                secaoQuando(),
                secaoRepeticao(),
                secaoAvisos(),
                secaoSom(),
                secaoExtras());

        return forma;
    }

    // ----------------------------------------------------------- as seções

    private Secao secaoOQueE() {
        campoTitulo.setPromptText("Ex.: Reunião de alinhamento com o time");
        campoTitulo.getStyleClass().addAll("campo", "campo-grande");

        campoDescricao.setPromptText("Pauta, link da sala, o que levar... (opcional)");
        campoDescricao.getStyleClass().add("campo");
        campoDescricao.setPrefRowCount(3);
        campoDescricao.setWrapText(true);

        campoSensivel.setTooltip(new Tooltip(
                "O título e a descrição ficam cifrados no banco e só aparecem com o app destrancado."));

        VBox protecao = new VBox(4,
                campoSensivel,
                Secao.dica("Datas e horários continuam legíveis, para o aviso sair no horário "
                        + "mesmo com o aplicativo trancado."));

        return new Secao(Icone.Simbolo.TEXTO, "O QUE É",
                Secao.comRotulo("Título", campoTitulo),
                Secao.comRotulo("Descrição", campoDescricao),
                protecao);
    }

    private Secao secaoQuando() {
        campoData.getStyleClass().add("campo");
        campoData.setPrefWidth(190);
        campoData.setConverter(ConversorData.BRASILEIRO);

        campoHora.getStyleClass().add("campo");
        campoHora.setPromptText("HH:MM");
        campoHora.setPrefWidth(110);

        HBox linha = new HBox(18,
                Secao.comRotulo("Data", campoData),
                Secao.comRotulo("Hora", campoHora));
        linha.setAlignment(Pos.BOTTOM_LEFT);

        return new Secao(Icone.Simbolo.CALENDARIO, "QUANDO ACONTECE",
                "O horário do compromisso em si. Os avisos saem antes dele.",
                linha,
                Secao.dica("Formato de 24 horas. Aceita 14:30, 1430 ou apenas 14."));
    }

    private Secao secaoRepeticao() {
        campoTipo.getItems().addAll(TipoRecorrencia.values());
        campoTipo.getStyleClass().add("campo");
        campoTipo.setPrefWidth(300);
        campoTipo.setOnAction(e -> ajustarCamposVisiveis());

        montarBlocoDiasSemana();
        montarBlocoDiaMes();
        montarBlocoIntervalo();
        montarBlocoFim();

        return new Secao(Icone.Simbolo.REPETIR, "COMO SE REPETE",
                campoTipo,
                blocoDiasSemana,
                blocoDiaMes,
                blocoIntervalo,
                blocoFim);
    }

    private Secao secaoAvisos() {
        FlowPane atalhos = new FlowPane(8, 8);
        for (Map.Entry<String, Integer> entrada : ATALHOS_DE_AVISO.entrySet()) {
            ToggleButton botao = new ToggleButton(entrada.getKey());
            botao.getStyleClass().add("chip-dia");
            botoesAviso.put(entrada.getValue(), botao);
            atalhos.getChildren().add(botao);
        }

        campoAvisoCustom.setPromptText("Outros, em minutos. Ex.: 45, 90");
        campoAvisoCustom.getStyleClass().add("campo");
        campoAvisoCustom.setPrefWidth(260);

        return new Secao(Icone.Simbolo.LEMBRETE, "QUANDO AVISAR",
                "Pode marcar mais de um. Cada marcação vira um aviso separado.",
                atalhos,
                Secao.comRotulo("Antecedências próprias", campoAvisoCustom));
    }

    private Secao secaoSom() {
        Button ouvir = new Button("Ouvir");
        ouvir.getStyleClass().add("botao");
        ouvir.setGraphic(Icone.de(Icone.Simbolo.SOM, 16));
        ouvir.setOnAction(e -> WindowsIntegracao.tocarAlerta());
        ouvir.disableProperty().bind(campoSom.selectedProperty().not());

        HBox linha = new HBox(14, campoSom, ouvir);
        linha.setAlignment(Pos.CENTER_LEFT);

        return new Secao(Icone.Simbolo.SOM, "SOM",
                linha,
                Secao.dica("Desmarque para este lembrete aparecer em silêncio. "
                        + "Qual som toca é escolhido uma vez só, em Configurações."));
    }

    private Secao secaoExtras() {
        campoCor.getStyleClass().add("campo");
        campoCor.setPrefWidth(150);

        campoAcao.setPromptText("Link, pasta, arquivo ou programa (opcional)");
        campoAcao.getStyleClass().add("campo");

        return new Secao(Icone.Simbolo.IDEIA, "EXTRAS",
                Secao.comRotulo("Cor da etiqueta", campoCor),
                Secao.comRotulo("Abrir junto com o alerta", campoAcao),
                Secao.dica("O alerta ganha um botão Abrir. Serve para a sala da reunião, "
                        + "uma pasta de projeto ou um .bat de rotina."));
    }

    private void montarBlocoDiasSemana() {
        FlowPane dias = new FlowPane(8, 8);
        String[] nomes = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom"};
        DayOfWeek[] valores = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};

        for (int i = 0; i < valores.length; i++) {
            ToggleButton botao = new ToggleButton(nomes[i]);
            botao.getStyleClass().add("chip-dia");
            botoesDia.put(valores[i], botao);
            dias.getChildren().add(botao);
        }
        blocoDiasSemana.getChildren().addAll(Secao.campo("Em quais dias"), dias);
        blocoDiasSemana.setManaged(false);
        blocoDiasSemana.setVisible(false);
    }

    private void montarBlocoDiaMes() {
        campoDiaMes.getStyleClass().add("campo");
        campoDiaMes.setPrefWidth(110);
        campoDiaMes.setEditable(true);

        Label explicacao = new Label("Em meses mais curtos, o aviso vai para o último dia do mês.");
        explicacao.getStyleClass().add("texto-fraco");
        explicacao.setWrapText(true);

        HBox linha = new HBox(10, new Label("Todo dia"), campoDiaMes, new Label("do mês"));
        linha.setAlignment(Pos.CENTER_LEFT);

        blocoDiaMes.getChildren().addAll(Secao.campo("Dia do mês"), linha, explicacao);
        blocoDiaMes.setManaged(false);
        blocoDiaMes.setVisible(false);
    }

    private void montarBlocoIntervalo() {
        campoIntervalo.getStyleClass().add("campo");
        campoIntervalo.setPrefWidth(120);
        campoIntervalo.setEditable(true);

        HBox linha = new HBox(10, new Label("A cada"), campoIntervalo, new Label("minutos"));
        linha.setAlignment(Pos.CENTER_LEFT);

        Label explicacao = new Label("útil para pausas, alongamento ou conferir uma fila de processamento.");
        explicacao.getStyleClass().add("texto-fraco");
        explicacao.setWrapText(true);

        blocoIntervalo.getChildren().addAll(Secao.campo("Intervalo"), linha, explicacao);
        blocoIntervalo.setManaged(false);
        blocoIntervalo.setVisible(false);
    }

    private void montarBlocoFim() {
        campoFim.getStyleClass().add("campo");
        campoFim.setPrefWidth(180);
        campoFim.setConverter(ConversorData.BRASILEIRO);
        campoFim.disableProperty().bind(campoTemFim.selectedProperty().not());

        HBox linha = new HBox(10, campoTemFim, campoFim);
        linha.setAlignment(Pos.CENTER_LEFT);

        blocoFim.getChildren().addAll(Secao.campo("Até quando"), linha);
        blocoFim.setManaged(false);
        blocoFim.setVisible(false);
    }

    /** Mostra apenas os campos que fazem sentido para o tipo escolhido. */
    private void ajustarCamposVisiveis() {
        TipoRecorrencia tipo = campoTipo.getValue();
        if (tipo == null) {
            return;
        }
        alternar(blocoDiasSemana, tipo.usaDiasDaSemana());
        alternar(blocoDiaMes, tipo.usaDiaDoMes());
        alternar(blocoIntervalo, tipo.usaIntervalo());
        alternar(blocoFim, tipo.eRepetido());
    }

    private void alternar(Region bloco, boolean visivel) {
        bloco.setVisible(visivel);
        bloco.setManaged(visivel);
    }

    // ------------------------------------------------------- carregar/coletar

    private void carregarValores() {
        campoTitulo.setText(lembrete.getTitulo());
        campoDescricao.setText(lembrete.getDescricao());
        campoSensivel.setSelected(lembrete.isSensivel());
        campoSensivel.setDisable(!SecurityService.estaDestrancado());
        if (!SecurityService.estaDestrancado()) {
            campoSensivel.setText("Proteger com a senha mestra (destranque o app para usar)");
        }

        campoTipo.setValue(lembrete.getTipo());
        campoData.setValue(lembrete.getInicio().toLocalDate());
        campoHora.setText(String.format("%02d:%02d",
                lembrete.getInicio().getHour(), lembrete.getInicio().getMinute()));

        if (lembrete.getFim() != null) {
            campoTemFim.setSelected(true);
            campoFim.setValue(lembrete.getFim().toLocalDate());
        }
        for (DayOfWeek dia : lembrete.getDiasSemana()) {
            ToggleButton b = botoesDia.get(dia);
            if (b != null) {
                b.setSelected(true);
            }
        }
        if (lembrete.getDiaMes() != null) {
            campoDiaMes.getValueFactory().setValue(lembrete.getDiaMes());
        }
        if (lembrete.getIntervaloMinutos() != null) {
            campoIntervalo.getValueFactory().setValue(lembrete.getIntervaloMinutos());
        }

        // Antecedências: as que tem atalho marcam o botao, as demais vao para o campo livre.
        List<String> livres = new ArrayList<>();
        for (Integer minutos : lembrete.getAntecedencias()) {
            ToggleButton botao = botoesAviso.get(minutos);
            if (botao != null) {
                botao.setSelected(true);
            } else {
                livres.add(String.valueOf(minutos));
            }
        }
        campoAvisoCustom.setText(String.join(", ", livres));

        try {
            campoCor.setValue(Color.web(lembrete.getCor()));
        } catch (Exception e) {
            campoCor.setValue(Color.web("#4c8dff"));
        }
        campoAcao.setText(lembrete.getAcao());
        campoSom.setSelected(lembrete.isSomAtivo());

        ajustarCamposVisiveis();
    }

    private Lembrete coletarValores() {
        lembrete.setTitulo(campoTitulo.getText() == null ? "" : campoTitulo.getText().trim());
        lembrete.setDescricao(campoDescricao.getText() == null ? "" : campoDescricao.getText().trim());
        lembrete.setSensivel(campoSensivel.isSelected());
        lembrete.setTipo(campoTipo.getValue());

        LocalDate data = campoData.getValue() == null ? LocalDate.now() : campoData.getValue();
        lembrete.setInicio(LocalDateTime.of(data, interpretarHora(campoHora.getText())));

        lembrete.setFim(campoTemFim.isSelected() && campoFim.getValue() != null
                ? campoFim.getValue().atTime(23, 59)
                : null);

        LinkedHashSet<DayOfWeek> dias = new LinkedHashSet<>();
        botoesDia.forEach((dia, botao) -> {
            if (botao.isSelected()) {
                dias.add(dia);
            }
        });
        lembrete.setDiasSemana(dias);

        lembrete.setDiaMes(campoTipo.getValue() == TipoRecorrencia.MENSAL ? campoDiaMes.getValue() : null);
        lembrete.setIntervaloMinutos(
                campoTipo.getValue() == TipoRecorrencia.INTERVALO ? campoIntervalo.getValue() : null);

        lembrete.setAntecedencias(coletarAntecedencias());
        lembrete.setCor(paraHex(campoCor.getValue()));
        lembrete.setAcao(campoAcao.getText() == null ? "" : campoAcao.getText().trim());
        lembrete.setSomAtivo(campoSom.isSelected());

        return lembrete;
    }

    private List<Integer> coletarAntecedencias() {
        LinkedHashSet<Integer> minutos = new LinkedHashSet<>();
        botoesAviso.forEach((valor, botao) -> {
            if (botao.isSelected()) {
                minutos.add(valor);
            }
        });
        String livre = campoAvisoCustom.getText();
        if (livre != null && !livre.isBlank()) {
            for (String parte : livre.split("[,;]")) {
                try {
                    int valor = Integer.parseInt(parte.trim());
                    if (valor >= 0) {
                        minutos.add(valor);
                    }
                } catch (NumberFormatException ignorado) {
                    // Texto inválido no campo livre e simplesmente desprezado.
                }
            }
        }
        if (minutos.isEmpty()) {
            minutos.add(0);   // sem escolha, avisa na hora exata
        }
        List<Integer> lista = new ArrayList<>(minutos);
        lista.sort(Integer::compareTo);
        return lista;
    }

    /** Aceita "14:30", "1430" e "14" - os três jeitos de digitar as pressas. */
    private LocalTime interpretarHora(String texto) {
        if (texto == null || texto.isBlank()) {
            return LocalTime.of(9, 0);
        }
        String limpo = texto.trim().replace("h", ":").replace(".", ":");
        try {
            if (limpo.contains(":")) {
                String[] partes = limpo.split(":");
                int hora = Integer.parseInt(partes[0].trim());
                int minuto = partes.length > 1 && !partes[1].isBlank()
                        ? Integer.parseInt(partes[1].trim()) : 0;
                return LocalTime.of(Math.floorMod(hora, 24), Math.floorMod(minuto, 60));
            }
            if (limpo.length() == 4) {
                return LocalTime.of(
                        Math.floorMod(Integer.parseInt(limpo.substring(0, 2)), 24),
                        Math.floorMod(Integer.parseInt(limpo.substring(2)), 60));
            }
            return LocalTime.of(Math.floorMod(Integer.parseInt(limpo), 24), 0);
        } catch (Exception e) {
            return LocalTime.of(9, 0);
        }
    }

    private String paraHex(Color cor) {
        if (cor == null) {
            return "#4c8dff";
        }
        return String.format("#%02X%02X%02X",
                (int) Math.round(cor.getRed() * 255),
                (int) Math.round(cor.getGreen() * 255),
                (int) Math.round(cor.getBlue() * 255));
    }
}
