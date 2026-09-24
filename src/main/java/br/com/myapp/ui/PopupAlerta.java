package br.com.myapp.ui;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.core.Scheduler;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.security.SecurityService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * O aviso que aparece na tela quando um lembrete dispara.
 *
 * Substitui o MsgBox do VBS usado pelo Lembrete.bat, com três diferenças que
 * mudam o uso no dia a dia:
 *
 *  - Não rouba o foco do que você esta digitando;
 *  - permite adiar sem precisar recadastrar nada;
 *  - varios avisos simultâneos se empilham em vez de se sobreporem.
 *
 * Um lembrete protegido com o aplicativo trancado mostra apenas o horário,
 * nunca o conteúdo.
 */
public class PopupAlerta {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    /** Avisos abertos no momento, do mais novo para o mais antigo. */
    private static final List<Stage> ABERTOS = new ArrayList<>();

    private static final int LARGURA = 380;
    private static final int MARGEM = 16;

    private static final LembreteService SERVICO = new LembreteService();

    private PopupAlerta() {
    }

    /** Mostra o aviso. Pode ser chamado de qualquer thread. */
    public static void mostrar(Scheduler.AlertaDisparado alerta) {
        Platform.runLater(() -> {
            try {
                construir(alerta);
                // Cada lembrete decide se faz barulho: uma reunião importante
                // toca, um lembrete de rotina pode aparecer em silêncio.
                if (alerta.lembrete().isSomAtivo()) {
                    WindowsIntegracao.tocarAlerta();
                }
            } catch (Exception e) {
                Log.erro("Falha ao exibir o alerta do lembrete " + alerta.lembrete().getId(), e);
            }
        });
    }

    private static void construir(Scheduler.AlertaDisparado alerta) {
        Lembrete lembrete = alerta.lembrete();
        boolean esconderConteudo = lembrete.isSensivel() && !SecurityService.estaDestrancado();

        Stage janela = new Stage(StageStyle.TRANSPARENT);
        janela.setAlwaysOnTop(true);
        // não entra na barra de tarefas e não rouba o foco da janela ativa.
        janela.initModality(javafx.stage.Modality.NONE);
        janela.setResizable(false);

        // ---------- Conteúdo ----------
        Label quando = new Label(alerta.resumoTempo().toUpperCase()
                + "  •  " + HORA.format(alerta.ocorrencia()));
        quando.getStyleClass().add("quando-alerta");

        Label titulo = new Label(esconderConteudo ? "Lembrete protegido" : lembrete.getTitulo());
        titulo.getStyleClass().add("titulo-alerta");
        titulo.setWrapText(true);
        if (esconderConteudo) {
            titulo.setGraphic(Icone.de(Icone.Simbolo.CADEADO, 17, "icone-atencao"));
        }

        VBox corpo = new VBox(4, quando, titulo);

        if (!esconderConteudo && lembrete.getDescricao() != null && !lembrete.getDescricao().isBlank()) {
            Label descricao = new Label(lembrete.getDescricao());
            descricao.getStyleClass().add("texto-fraco");
            descricao.setWrapText(true);
            descricao.setMaxWidth(LARGURA - 60);
            corpo.getChildren().add(descricao);
        }

        if (alerta.atrasado()) {
            Label atrasado = new Label("Este aviso estava pendente desde "
                    + DATA_HORA.format(alerta.ocorrencia().minusMinutes(alerta.antecedencia())) + ".");
            atrasado.getStyleClass().add("texto-alerta");
            atrasado.setWrapText(true);
            atrasado.setMaxWidth(LARGURA - 60);
            corpo.getChildren().add(atrasado);
        }

        if (esconderConteudo) {
            Label aviso = new Label("Destranque o MyApp para ver o conteúdo.");
            aviso.getStyleClass().add("texto-fraco");
            corpo.getChildren().add(aviso);
        }

        // ---------- botoes ----------
        int minutosSnooze = Config.get().minutosSnooze;

        HBox botoes = new HBox(8);
        botoes.setAlignment(Pos.CENTER_RIGHT);

        Button adiar = new Button("Adiar " + minutosSnooze + " min");
        adiar.getStyleClass().add("botao");
        adiar.setOnAction(e -> {
            SERVICO.adiar(lembrete.getId(), alerta.ocorrencia(), minutosSnooze);
            fechar(janela);
        });

        Button ok = new Button("Confirmar");
        ok.getStyleClass().add("botao-primario");
        ok.setOnAction(e -> {
            boolean arquivado = SERVICO.reconhecer(lembrete.getId(), alerta.ocorrencia());
            if (arquivado) {
                // Avisa antes de fechar: o lembrete vai sumir da lista, e sumir
                // sem explicação deixa a impressão de que algo se perdeu.
                confirmarEFechar(janela, corpo, botoes);
            } else {
                fechar(janela);
            }
        });

        // Botao extra quando o lembrete tem link, pasta ou programa associado.
        if (!esconderConteudo && lembrete.getAcao() != null && !lembrete.getAcao().isBlank()) {
            Button abrir = new Button("Abrir");
            abrir.getStyleClass().add("botao");
            abrir.setOnAction(e -> {
                SERVICO.executarAcao(lembrete);
                SERVICO.reconhecer(lembrete.getId(), alerta.ocorrencia());
                fechar(janela);
            });
            botoes.getChildren().add(abrir);
        }

        Region espaco = new Region();
        HBox.setHgrow(espaco, javafx.scene.layout.Priority.ALWAYS);
        botoes.getChildren().addAll(adiar, ok);

        VBox raiz = new VBox(12, corpo, botoes);
        raiz.getStyleClass().addAll("raiz", "popup-alerta");
        if ("claro".equals(Config.get().tema)) {
            raiz.getStyleClass().add("claro");
        }
        raiz.setPrefWidth(LARGURA);
        raiz.setMaxWidth(LARGURA);

        Scene cena = new Scene(raiz);
        cena.setFill(Color.TRANSPARENT);
        cena.getStylesheets().add(PopupAlerta.class.getResource("/css/app.css").toExternalForm());

        janela.setScene(cena);
        janela.setOnHidden(e -> {
            ABERTOS.remove(janela);
            reposicionar();
        });

        ABERTOS.add(0, janela);
        janela.show();
        reposicionar();

        raiz.setOpacity(0);
        FadeTransition surgir = new FadeTransition(Duration.millis(180), raiz);
        surgir.setToValue(1);
        surgir.play();
    }

    private static void fechar(Stage janela) {
        FadeTransition sumir = new FadeTransition(Duration.millis(140), janela.getScene().getRoot());
        sumir.setToValue(0);
        sumir.setOnFinished(e -> janela.close());
        sumir.play();
    }

    /**
     * Confirma um lembrete que se encerrou, avisando que ele foi arquivado.
     *
     * O lembrete some da lista logo em seguida; some sem explicação, dá a
     * impressão de que algo se perdeu. A mensagem dura pouco mais de um
     * segundo e já diz onde encontrá-lo.
     */
    private static void confirmarEFechar(Stage janela, VBox corpo, HBox botoes) {
        botoes.setVisible(false);
        botoes.setManaged(false);

        Label concluido = new Label("Concluído — arquivado na lixeira");
        concluido.setGraphic(Icone.de(Icone.Simbolo.CONFIRMAR, 15, "icone-sucesso"));
        concluido.getStyleClass().add("texto-ok");
        concluido.setStyle("-fx-font-weight: bold;");
        corpo.getChildren().add(concluido);

        PauseTransition espera = new PauseTransition(Duration.millis(1300));
        espera.setOnFinished(e -> fechar(janela));
        espera.play();
    }

    /** Empilha os avisos abertos no canto inferior direito. */
    private static void reposicionar() {
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        double y = area.getMaxY() - MARGEM;

        for (Stage janela : ABERTOS) {
            double altura = janela.getHeight();
            if (Double.isNaN(altura) || altura <= 0) {
                altura = 150;
            }
            y -= altura;
            janela.setX(area.getMaxX() - LARGURA - MARGEM);
            janela.setY(y);
            y -= 10;   // respiro entre os avisos

            // Passou do topo da tela: para de empilhar para não sair da área visível.
            if (y < area.getMinY()) {
                break;
            }
        }
    }

    /** Fecha todos os avisos. Usado quando o aplicativo tranca. */
    public static void fecharTodos() {
        Platform.runLater(() -> {
            for (Stage janela : new ArrayList<>(ABERTOS)) {
                janela.close();
            }
            ABERTOS.clear();
        });
    }
}
