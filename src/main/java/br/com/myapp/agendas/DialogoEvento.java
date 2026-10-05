package br.com.myapp.agendas;

import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteEditor;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.modules.lembretes.TipoRecorrencia;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * O evento do Google por inteiro: o que a linha da agenda resume.
 *
 * Só leitura, como tudo que vem do Google. As três saídas são as que fazem
 * sentido a partir daqui: entrar na reunião, abrir o dia no Google Agenda
 * (para alterar) e criar um lembrete do MyApp a partir do evento — para um
 * aviso só seu, como "levar o contrato", sem mexer no convite.
 */
public class DialogoEvento {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final Window dono;
    private final AgendaService.EventoNaAgenda item;

    /**
     * Pedido de "Criar lembrete a partir deste", atendido só depois que esta
     * janela termina de fechar. Abrir o cadastro dentro do próprio clique, com
     * esta janela ainda fechando, deixava o cadastro "travado" no Windows: a
     * janela velha seguia dona do mouse, e a nova recebia cliques, mas não o
     * movimento — sem cursor de mão, sem rolagem, sem nada reagindo.
     */
    private boolean criarLembreteAoFechar;

    public DialogoEvento(Window dono, AgendaService.EventoNaAgenda item) {
        this.dono = dono;
        this.item = item;
    }

    public void abrir() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(item.evento().titulo());
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);
        dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonType.CLOSE.getButtonData()));
        dialogo.getDialogPane().setContent(Dialogos.rolavel(montar(dialogo), 520, 560));
        Dialogos.aplicarTema(dialogo.getDialogPane());
        dialogo.showAndWait();
        if (criarLembreteAoFechar) {
            criarLembrete();
        }
    }

    private VBox montar(Dialog<ButtonType> dialogo) {
        Evento e = item.evento();
        Agenda agenda = item.agenda();

        // ---------- cabeçalho: cor, título, origem ----------
        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-cor");
        faixa.setStyle("-fx-background-color: " + agenda.getCor() + ";");

        Label titulo = new Label(e.titulo());
        titulo.getStyleClass().add("titulo-evento");
        titulo.setWrapText(true);
        titulo.setMinHeight(Region.USE_PREF_SIZE);

        Label selo = new Label("G");
        selo.getStyleClass().add("selo-agenda");
        Label origem = new Label("Google Agenda · " + agenda.getNome());
        origem.getStyleClass().add("texto-fraco");
        HBox linhaOrigem = new HBox(8, selo, origem);
        linhaOrigem.setAlignment(Pos.CENTER_LEFT);

        VBox textosTopo = new VBox(6, titulo, linhaOrigem);
        HBox.setHgrow(textosTopo, Priority.ALWAYS);
        HBox topo = new HBox(14, faixa, textosTopo);

        // ---------- quando e onde ----------
        VBox detalhes = new VBox(8, comIcone(Icone.Simbolo.RELOGIO, quando(e)));
        if (!e.local().isBlank()) {
            detalhes.getChildren().add(comIcone(Icone.Simbolo.EMPRESA, e.local()));
        }

        // ---------- ações ----------
        FlowPane acoes = new FlowPane(8, 8);
        if (e.linkReuniao() != null) {
            Button entrar = Botoes.primario("Entrar na reunião", Icone.Simbolo.ABRIR_FORA);
            entrar.setOnAction(a -> abrir(e.linkReuniao()));
            acoes.getChildren().add(entrar);
        }
        Button noGoogle = Botoes.comum("Abrir no Google Agenda", Icone.Simbolo.CALENDARIO);
        Botoes.instalarDica(noGoogle, "Abre o dia do evento no Google Agenda, onde dá para alterá-lo");
        noGoogle.setOnAction(a -> abrir(linkDoDia(e.inicio().toLocalDate())));

        Button lembrete = Botoes.comum("Criar lembrete a partir deste", Icone.Simbolo.LEMBRETE);
        Botoes.instalarDica(lembrete, "Um lembrete seu, com o horário e o link do evento já preenchidos");
        lembrete.setOnAction(a -> {
            criarLembreteAoFechar = true;
            dialogo.close();
        });
        acoes.getChildren().addAll(noGoogle, lembrete);

        VBox forma = new VBox(18, topo, detalhes, acoes);

        // ---------- descrição inteira ----------
        if (!e.descricao().isBlank()) {
            // Área de texto só de leitura: dá para selecionar e copiar um
            // trecho da pauta, o que um rótulo não deixa.
            TextArea descricao = new TextArea(e.descricao());
            descricao.setEditable(false);
            descricao.setWrapText(true);
            descricao.getStyleClass().add("campo");
            descricao.setPrefRowCount(Math.min(14, Math.max(3, e.descricao().split("\n").length + 1)));
            forma.getChildren().add(new Secao(Icone.Simbolo.TEXTO, "DESCRIÇÃO", descricao));
        }

        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        return forma;
    }

    /** "Segunda-feira, 05 de outubro · 14:00 – 15:00" — ou "dia inteiro". */
    static String quando(Evento e) {
        String dia = maiuscula(DIA.format(e.inicio()));
        if (e.diaInteiro()) {
            LocalDate ultimo = e.fim().toLocalDate().minusDays(1);
            return ultimo.isAfter(e.inicio().toLocalDate())
                    ? dia + " a " + DIA.format(ultimo) + " · dia inteiro"
                    : dia + " · dia inteiro";
        }
        if (e.fim().toLocalDate().equals(e.inicio().toLocalDate())) {
            return dia + " · " + HORA.format(e.inicio()) + " – " + HORA.format(e.fim());
        }
        return dia + " " + HORA.format(e.inicio()) + " até " + DIA.format(e.fim()) + " " + HORA.format(e.fim());
    }

    /**
     * O dia no Google Agenda. O endereço exato de um evento depende de
     * identificadores que o .ics não traz com segurança; o do dia é estável e
     * leva a um clique do evento.
     */
    static String linkDoDia(LocalDate dia) {
        return "https://calendar.google.com/calendar/r/day/"
                + dia.getYear() + "/" + dia.getMonthValue() + "/" + dia.getDayOfMonth();
    }

    private void criarLembrete() {
        Evento e = item.evento();
        Lembrete modelo = new Lembrete();
        modelo.setTitulo(e.titulo());
        modelo.setDescricao(e.descricao());
        modelo.setTipo(TipoRecorrencia.UNICO);
        modelo.setInicio(e.diaInteiro() ? e.inicio().toLocalDate().atTime(item.agenda().getHoraDiaInteiro())
                : e.inicio());
        modelo.setAntecedencias(List.of(10));
        modelo.setCor(item.agenda().getCor());
        modelo.setSensivel(item.agenda().isProtegida());
        // O link da reunião vira a ação do lembrete: o alerta ganha o "Abrir".
        modelo.setAcao(e.linkReuniao() == null ? "" : e.linkReuniao());

        LembreteEditor.novoAPartirDe(dono, modelo).abrir().ifPresent(l -> {
            try {
                new LembreteService().salvar(l);
                Aviso.sucesso("Lembrete criado com sucesso!");
            } catch (IllegalArgumentException | IllegalStateException erro) {
                Dialogos.erro(dono, "Não foi possível criar o lembrete", erro.getMessage());
            }
        });
    }

    private static HBox comIcone(Icone.Simbolo simbolo, String texto) {
        Label rotulo = new Label(texto);
        rotulo.setWrapText(true);
        rotulo.setMinHeight(Region.USE_PREF_SIZE);
        HBox linha = new HBox(10, Icone.de(simbolo, 16), rotulo);
        linha.setAlignment(Pos.TOP_LEFT);
        return linha;
    }

    private static void abrir(String link) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(link));
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> Aviso.erro("Não foi possível abrir o navegador."));
            }
        }, "abrir-link").start();
    }

    private static String maiuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
