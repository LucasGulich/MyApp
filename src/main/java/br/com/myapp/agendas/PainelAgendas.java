package br.com.myapp.agendas;

import br.com.myapp.core.EventBus;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

/**
 * A lista de agendas conectadas, na aba "Agendas" das configurações.
 *
 * Cada agenda mostra se está em dia — "sincronizada há 3 min" — ou o que deu
 * errado na última tentativa, com a cópia que continua valendo. Os botões
 * agem na hora, sem esperar o "Salvar" da janela de configurações: conectar
 * uma agenda é um cadastro, não uma preferência.
 *
 * Quem abre o painel precisa chamar {@link #desligar()} ao fechar a janela.
 */
public class PainelAgendas extends VBox {

    private static final int DIAS_DO_RESUMO = 30;

    private final Window dono;
    private final VBox lista = new VBox(10);
    private final Consumer<AgendaService.AgendasMudaram> aoMudar =
            e -> Platform.runLater(this::recarregar);

    /** Mantém o "há 3 min" honesto enquanto a janela está aberta. */
    private final Timeline relogio = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> recarregar()));

    public PainelAgendas(Window dono) {
        this.dono = dono;
        setSpacing(14);

        Label rotulo = new Label("AGENDAS CONECTADAS");
        rotulo.getStyleClass().add("rotulo");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        Button conectar = Botoes.comum("Conectar agenda", Icone.Simbolo.ADICIONAR);
        conectar.setOnAction(e -> new DialogoAgenda(janela(), null).abrir());

        HBox topo = new HBox(10, rotulo, espaco, conectar);
        topo.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(topo, lista,
                explicacao("Os eventos aparecem na tela Hoje junto com os seus lembretes, e chegam "
                        + "com até " + AgendaService.MINUTOS_ENTRE_SINCRONIZACOES + " minutos de "
                        + "atraso. Aqui eles são só para leitura: criar e alterar continua sendo "
                        + "no Google."),
                explicacao("Sem internet, vale a última cópia baixada."));

        EventBus.ouvir(AgendaService.AgendasMudaram.class, aoMudar);
        relogio.setCycleCount(Animation.INDEFINITE);
        relogio.play();
        recarregar();
    }

    /** Para de ouvir e de contar o tempo. Chame ao fechar a janela. */
    public void desligar() {
        EventBus.deixarDeOuvir(AgendaService.AgendasMudaram.class, aoMudar);
        relogio.stop();
    }

    private void recarregar() {
        lista.getChildren().clear();
        List<Agenda> agendas = AgendaService.listar();
        if (agendas.isEmpty()) {
            lista.getChildren().add(explicacao("Nenhuma agenda conectada ainda. Use \"Conectar "
                    + "agenda\" e cole o endereço secreto da sua agenda do Google."));
            return;
        }
        for (Agenda agenda : agendas) {
            lista.getChildren().add(cartao(agenda));
        }
    }

    private HBox cartao(Agenda agenda) {
        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-cor");
        faixa.setStyle("-fx-background-color: " + agenda.getCor() + ";");

        Label nome = new Label(agenda.getNome());
        nome.getStyleClass().add("titulo-compromisso");

        Label estado = new Label();
        estado.setWrapText(true);
        estado.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        estado.setMaxWidth(340);
        descreverEstado(agenda, estado);

        VBox textos = new VBox(3, nome, estado);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button sincronizar = Botoes.icone(Icone.Simbolo.GERAR, "Sincronizar agora");
        sincronizar.setOnAction(e -> sincronizar(agenda, sincronizar, estado));

        Button editar = Botoes.icone(Icone.Simbolo.EDITAR, "Editar");
        editar.setOnAction(e -> new DialogoAgenda(janela(), agenda).abrir());

        Button excluir = Botoes.iconePerigo(Icone.Simbolo.EXCLUIR, "Desconectar esta agenda");
        excluir.setOnAction(e -> excluir(agenda));

        HBox linha = new HBox(12, faixa, textos, sincronizar, editar, excluir);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("linha-compromisso");
        return linha;
    }

    private void descreverEstado(Agenda agenda, Label rotulo) {
        rotulo.getStyleClass().removeAll("texto-ok", "texto-alerta", "texto-fraco");
        if (agenda.getUltimoErro() != null) {
            String copia = agenda.jaSincronizou()
                    ? " Usando a cópia de " + quando(agenda.getSincronizadaEm()) + "."
                    : "";
            rotulo.setText("⚠ " + agenda.getUltimoErro() + copia);
            rotulo.getStyleClass().add("texto-alerta");
        } else if (!agenda.jaSincronizou()) {
            rotulo.setText("Sincronizando pela primeira vez…");
            rotulo.getStyleClass().add("texto-fraco");
        } else {
            int eventos = AgendaService.quantosNosProximosDias(agenda, DIAS_DO_RESUMO);
            rotulo.setText("✓ Sincronizada " + haQuanto(agenda.getSincronizadaEm()) + " · "
                    + eventos + (eventos == 1 ? " evento" : " eventos")
                    + " nos próximos " + DIAS_DO_RESUMO + " dias");
            rotulo.getStyleClass().add("texto-ok");
        }
    }

    private void sincronizar(Agenda agenda, Button botao, Label estado) {
        botao.setDisable(true);
        estado.setText("Sincronizando…");
        estado.getStyleClass().removeAll("texto-ok", "texto-alerta");
        estado.getStyleClass().add("texto-fraco");

        new Thread(() -> {
            boolean ok = AgendaService.sincronizar(agenda.getId());
            Platform.runLater(() -> {
                botao.setDisable(false);
                if (ok) {
                    Aviso.sucesso("Agenda \"" + agenda.getNome() + "\" sincronizada!");
                } else {
                    Aviso.erro("A agenda \"" + agenda.getNome() + "\" não sincronizou.");
                }
                recarregar();
            });
        }, "sincronizar-agenda").start();
    }

    private void excluir(Agenda agenda) {
        boolean confirmou = Dialogos.confirmarExclusao(janela(),
                "a agenda \"" + agenda.getNome() + "\"",
                "Os eventos dela somem do MyApp. No Google nada muda.");
        if (confirmou) {
            AgendaService.excluir(agenda.getId());
            Aviso.sucesso("Agenda desconectada.");
        }
    }

    // -------------------------------------------------------------- apoio

    /** A janela de configurações, para os diálogos abrirem por cima dela. */
    private Window janela() {
        return getScene() != null && getScene().getWindow() != null ? getScene().getWindow() : dono;
    }

    /** "agora há pouco", "há 3 min", "há 2 h", "ontem às 17:40". */
    public static String haQuanto(LocalDateTime quando) {
        long minutos = ChronoUnit.MINUTES.between(quando, LocalDateTime.now());
        if (minutos < 1) {
            return "agora há pouco";
        }
        if (minutos < 60) {
            return "há " + minutos + " min";
        }
        if (minutos < 12 * 60) {
            return "há " + (minutos / 60) + " h";
        }
        return quando(quando);
    }

    private static String quando(LocalDateTime data) {
        LocalDate dia = data.toLocalDate();
        String hora = DateTimeFormatter.ofPattern("HH:mm").format(data);
        if (dia.equals(LocalDate.now())) {
            return "hoje às " + hora;
        }
        if (dia.equals(LocalDate.now().minusDays(1))) {
            return "ontem às " + hora;
        }
        return DateTimeFormatter.ofPattern("dd/MM").format(data) + " às " + hora;
    }

    private Label explicacao(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-fraco");
        l.setWrapText(true);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        l.setMaxWidth(520);
        return l;
    }
}
