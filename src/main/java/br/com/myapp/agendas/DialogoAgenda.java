package br.com.myapp.agendas;

import br.com.myapp.core.Texto;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conectar uma agenda do Google, ou editar uma já conectada.
 *
 * O botão "Testar" é o centro do formulário: baixa o endereço na hora e diz
 * quantos eventos vieram e qual é o próximo. Assim dá para ter certeza de que
 * o link colado é o da agenda certa antes de salvar.
 */
public class DialogoAgenda {

    private static final String COR_PADRAO = "#33B679";
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final Window dono;
    private final Agenda agenda;
    private final boolean ehNova;

    private final TextField campoNome = new TextField();
    private final ColorPicker campoCor = new ColorPicker();
    private final PasswordField campoLink = new PasswordField();
    private final TextField campoLinkVisivel = new TextField();
    private final Label resultadoTeste = new Label();
    private final CheckBox campoOcultarRecusados =
            new CheckBox("Esconder os convites que eu recusei");
    private final CheckBox campoProtegida =
            new CheckBox("Tratar como protegida");
    private final CheckBox campoSom = new CheckBox("Tocar som");
    private final CheckBox campoDiaInteiro =
            new CheckBox("Avisar também os de dia inteiro, às");
    private final TextField campoHoraDiaInteiro = new TextField();
    private final Map<Integer, ToggleButton> botoesAviso = new LinkedHashMap<>();

    /** Os atalhos de antecedência, na ordem em que aparecem. */
    private static final Map<String, Integer> ATALHOS = new LinkedHashMap<>();

    static {
        ATALHOS.put("Na hora", 0);
        ATALHOS.put("5 min", 5);
        ATALHOS.put("10 min", 10);
        ATALHOS.put("15 min", 15);
        ATALHOS.put("30 min", 30);
        ATALHOS.put("1 h", 60);
        ATALHOS.put("1 dia", 1440);
    }

    public DialogoAgenda(Window dono, Agenda existente) {
        this.dono = dono;
        this.ehNova = existente == null;
        this.agenda = existente == null ? new Agenda() : existente;
    }

    public void abrir() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(ehNova ? "Conectar agenda do Google" : "Editar agenda");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        ButtonType salvar = new ButtonType(ehNova ? "Conectar" : "Salvar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);
        // Com a seção de avisos o formulário ficou alto: sem a rolagem, o
        // "Salvar" caía para fora da tela.
        dialogo.getDialogPane().setContent(Dialogos.rolavel(montar(), 520, 640));
        dialogo.setResizable(true);

        Dialogos.aplicarTema(dialogo.getDialogPane());
        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        Button botaoSalvar = (Button) dialogo.getDialogPane().lookupButton(salvar);
        botaoSalvar.getStyleClass().add("botao-primario");

        // Valida sem fechar a janela quando algo estiver errado.
        botaoSalvar.addEventFilter(javafx.event.ActionEvent.ACTION, evento -> {
            try {
                coletar();
                AgendaService.salvar(agenda);
                Aviso.sucesso(ehNova
                        ? "Agenda conectada! Os eventos aparecem na tela Hoje em instantes."
                        : "Agenda atualizada.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                Dialogos.erro(dono, "Não foi possível salvar", e.getMessage());
                evento.consume();
            }
        });

        carregar();
        dialogo.showAndWait();
    }

    private VBox montar() {
        campoNome.setPromptText("Ex.: Trabalho");
        campoNome.getStyleClass().addAll("campo", "campo-grande");

        campoCor.getStyleClass().add("campo");
        campoCor.setPrefWidth(150);

        Secao identificacao = new Secao(Icone.Simbolo.CALENDARIO, "IDENTIFICAÇÃO",
                Secao.comRotulo("Nome", campoNome),
                Secao.comRotulo("Cor", campoCor),
                Secao.dica("A cor marca os eventos desta agenda na tela Hoje."));

        Secao endereco = new Secao(Icone.Simbolo.ELO, "ENDEREÇO SECRETO",
                montarCampoLink(),
                resultadoTeste,
                Secao.dica("Onde achar: Google Agenda → ⚙ Configurações → clique na sua agenda, "
                        + "em \"Configurações das minhas agendas\" → \"Integrar agenda\" → "
                        + "\"Endereço secreto no formato iCal\"."),
                Secao.dica("Quem tem este link lê a sua agenda inteira. Por isso ele é guardado "
                        + "cifrado, e não deve ser colado em mais lugar nenhum."));

        Secao opcoes = new Secao(Icone.Simbolo.FERRAMENTA, "OPÇÕES",
                campoOcultarRecusados,
                campoProtegida,
                Secao.dica("Protegida: com o MyApp trancado, os eventos aparecem como "
                        + "\"Evento protegido\", só com o horário — igual ao lembrete protegido."));

        // Opções longas quebram linha em vez de virar "...".
        for (CheckBox c : new CheckBox[]{campoOcultarRecusados, campoProtegida}) {
            c.setWrapText(true);
            c.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        resultadoTeste.setWrapText(true);
        resultadoTeste.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        resultadoTeste.setMaxWidth(440);
        resultadoTeste.setVisible(false);
        resultadoTeste.setManaged(false);

        VBox forma = new VBox(18, identificacao, endereco, montarAvisos(), opcoes);
        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(500);
        return forma;
    }

    /**
     * Quando avisar, com os mesmos atalhos em ficha do cadastro de lembrete.
     * Nenhum marcado é uma escolha válida: a agenda aparece na tela, mas não
     * avisa.
     */
    private Secao montarAvisos() {
        FlowPane atalhos = new FlowPane(8, 8);
        ATALHOS.forEach((nome, minutos) -> {
            ToggleButton botao = new ToggleButton(nome);
            botao.getStyleClass().add("chip-dia");
            botoesAviso.put(minutos, botao);
            atalhos.getChildren().add(botao);
        });
        // "Avisar também os de dia inteiro, às [09:00]": a hora fica na própria
        // frase, e só vale com a opção marcada.
        campoHoraDiaInteiro.getStyleClass().add("campo");
        campoHoraDiaInteiro.setPromptText("09:00");
        campoHoraDiaInteiro.setPrefWidth(76);
        campoHoraDiaInteiro.disableProperty().bind(campoDiaInteiro.selectedProperty().not());
        Botoes.instalarDica(campoHoraDiaInteiro, "Aceita 8, 8h30, 0830 ou 08:30");
        // Ao sair do campo, mostra a hora como foi entendida: "8h30" vira
        // "08:30", e algo que não é hora volta para a anterior.
        campoHoraDiaInteiro.focusedProperty().addListener((obs, antes, focado) -> {
            if (!focado) {
                campoHoraDiaInteiro.setText(HORA.format(horaDiaInteiroDigitada()));
            }
        });
        HBox linhaDiaInteiro = new HBox(8, campoDiaInteiro, campoHoraDiaInteiro);
        linhaDiaInteiro.setAlignment(Pos.CENTER_LEFT);

        return new Secao(Icone.Simbolo.LEMBRETE, "QUANDO AVISAR",
                "Antes de cada evento. Pode marcar mais de um; nenhum marcado, a agenda não avisa.",
                atalhos,
                campoSom,
                linhaDiaInteiro,
                Secao.dica("O aviso traz o botão Entrar quando a reunião tem link do Meet, "
                        + "Teams ou Zoom."));
    }

    /**
     * O link fica escondido como senha, com o olho para conferir — dois
     * controles empilhados, como no campo de segredo das notas, porque o
     * JavaFX não tem um campo que alterne entre esconder e mostrar.
     */
    private VBox montarCampoLink() {
        campoLink.getStyleClass().add("campo");
        campoLink.setPromptText("https://calendar.google.com/calendar/ical/…/basic.ics");

        campoLinkVisivel.getStyleClass().add("campo");
        campoLinkVisivel.setVisible(false);
        campoLinkVisivel.setManaged(false);
        campoLinkVisivel.textProperty().bindBidirectional(campoLink.textProperty());

        StackPane pilha = new StackPane(campoLink, campoLinkVisivel);
        HBox.setHgrow(pilha, Priority.ALWAYS);

        Button ver = Botoes.icone(Icone.Simbolo.OLHO, "Mostrar ou esconder");
        ver.setOnAction(e -> {
            boolean mostrando = campoLinkVisivel.isVisible();
            campoLinkVisivel.setVisible(!mostrando);
            campoLinkVisivel.setManaged(!mostrando);
            campoLink.setVisible(mostrando);
            campoLink.setManaged(mostrando);
        });

        Button testar = Botoes.comum("Testar", Icone.Simbolo.CONFIRMAR);
        Botoes.instalarDica(testar, "Baixa a agenda agora, sem salvar, para conferir o link");
        testar.setOnAction(e -> testar(testar));

        HBox linha = new HBox(8, pilha, ver, testar);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, Secao.campo("Link"), linha);
    }

    private void testar(Button botao) {
        String link = campoLink.getText();
        botao.setDisable(true);
        mostrarResultado("Testando…", "texto-fraco");

        // A rede fica fora da thread da tela: a janela não pode congelar
        // enquanto o Google responde.
        new Thread(() -> {
            try {
                AgendaService.ResultadoTeste r = AgendaService.testar(link);
                String proximo = r.proximo()
                        .map(e -> " Próximo: " + DIA_HORA.format(e.inicio()) + " — \"" + e.titulo() + "\".")
                        .orElse(" Nenhum evento nos próximos dias.");
                Platform.runLater(() -> mostrarResultado(
                        "✓ Conexão OK — " + r.eventos() + (r.eventos() == 1 ? " evento" : " eventos")
                                + " na agenda." + proximo, "texto-ok"));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> mostrarResultado("✗ " + e.getMessage(), "texto-perigo"));
            } finally {
                Platform.runLater(() -> botao.setDisable(false));
            }
        }, "testar-agenda").start();
    }

    private void mostrarResultado(String texto, String estilo) {
        resultadoTeste.setText(texto);
        resultadoTeste.getStyleClass().removeAll("texto-fraco", "texto-ok", "texto-perigo");
        resultadoTeste.getStyleClass().add(estilo);
        resultadoTeste.setVisible(true);
        resultadoTeste.setManaged(true);
    }

    private void carregar() {
        campoNome.setText(agenda.getNome());
        campoOcultarRecusados.setSelected(agenda.isOcultarRecusados());
        campoProtegida.setSelected(agenda.isProtegida());
        campoSom.setSelected(agenda.isSomAtivo());
        campoDiaInteiro.setSelected(agenda.isAvisarDiaInteiro());
        campoHoraDiaInteiro.setText(HORA.format(agenda.getHoraDiaInteiro()));
        botoesAviso.forEach((minutos, botao) -> botao.setSelected(agenda.getAntecedencias().contains(minutos)));
        if (!ehNova) {
            campoLink.setText(AgendaService.link(agenda.getId()).orElse(""));
        }
        try {
            campoCor.setValue(Color.web(ehNova ? COR_PADRAO : agenda.getCor()));
        } catch (Exception e) {
            campoCor.setValue(Color.web(COR_PADRAO));
        }
    }

    private void coletar() {
        agenda.setNome(campoNome.getText() == null ? "" : campoNome.getText().trim());
        agenda.setLink(campoLink.getText());
        agenda.setCor(paraHex(campoCor.getValue()));
        agenda.setOcultarRecusados(campoOcultarRecusados.isSelected());
        agenda.setProtegida(campoProtegida.isSelected());
        agenda.setSomAtivo(campoSom.isSelected());
        agenda.setAvisarDiaInteiro(campoDiaInteiro.isSelected());
        agenda.setHoraDiaInteiro(horaDiaInteiroDigitada());
        List<Integer> marcadas = new ArrayList<>();
        botoesAviso.forEach((minutos, botao) -> {
            if (botao.isSelected()) {
                marcadas.add(minutos);
            }
        });
        // Antecedência que não tem ficha (gravada por versão futura, ou à mão)
        // não pode sumir só porque o formulário foi aberto.
        agenda.getAntecedencias().stream()
                .filter(m -> !botoesAviso.containsKey(m))
                .forEach(marcadas::add);
        marcadas.sort(Integer::compareTo);
        agenda.setAntecedencias(marcadas);
    }

    /** A hora do campo; vazia ou inválida, fica a que a agenda já tinha. */
    private LocalTime horaDiaInteiroDigitada() {
        return Texto.interpretarHora(campoHoraDiaInteiro.getText(), agenda.getHoraDiaInteiro());
    }

    private String paraHex(Color cor) {
        if (cor == null) {
            return COR_PADRAO;
        }
        return String.format("#%02X%02X%02X",
                (int) Math.round(cor.getRed() * 255),
                (int) Math.round(cor.getGreen() * 255),
                (int) Math.round(cor.getBlue() * 255));
    }
}
