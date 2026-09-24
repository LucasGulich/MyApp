package br.com.myapp;

import br.com.myapp.backup.BackupService;
import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.core.Scheduler;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.data.Database;
import br.com.myapp.modules.ModuleRegistry;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.security.SecurityService;
import br.com.myapp.security.SessionManager;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.BandejaSistema;
import br.com.myapp.ui.BarraTitulo;
import br.com.myapp.ui.CampoBusca;
import br.com.myapp.ui.IconeApp;
import br.com.myapp.ui.PopupAlerta;
import br.com.myapp.ui.RedimensionadorJanela;
import br.com.myapp.ui.Shell;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Montagem do aplicativo.
 *
 * Ordem da inicialização:
 *
 *   1. banco aberto e migrado
 *   2. módulos registrados
 *   3. janela montada (tela de cadastro na primeira vez, de bloqueio depois)
 *   4. agendador, vigia de inatividade e backup automático ligados
 *
 * O agendador sobe independente do bloqueio: os avisos precisam sair no
 * horário mesmo com o aplicativo trancado.
 */
public class App extends Application {

    private Shell shell;
    private Stage janela;
    private BandejaSistema bandeja;
    private boolean encerrandoDeVerdade;

    @Override
    public void start(Stage palco) {
        this.janela = palco;

        try {
            Database.conexao();          // abre e migra
            Log.limparAntigos();
            ModuleRegistry.registrarPadroes();

            montarJanela();
            ligarServicos();
            tratarPrimeiraExecucao();

        } catch (Exception e) {
            Log.erro("Falha fatal na inicialização", e);
            mostrarErroFatal(e);
        }
    }

    // ------------------------------------------------------------- janela

    private void montarJanela() {
        shell = new Shell(this::encerrar);

        // Sem a moldura do Windows: a barra de título é desenhada pelo próprio
        // aplicativo, para acompanhar o tema claro e o escuro. Ver BarraTitulo.
        janela.initStyle(StageStyle.UNDECORATED);

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("janela-principal");
        raiz.setTop(new BarraTitulo(janela, "MyApp", true, true));
        raiz.setCenter(shell);

        // A pilha existe para os avisos de "salvo com sucesso" flutuarem sobre
        // a janela sem empurrar o conteúdo. Ver Aviso.
        //
        // É ela, e não o BorderPane, que carrega a classe "raiz": as cores do
        // tema são declaradas ali e valem para os descendentes, e os avisos
        // são irmãos do conteúdo — se a declaração ficasse no BorderPane, o
        // aviso nasceria fora do alcance dela e sem cor nenhuma.
        StackPane pilha = new StackPane(raiz);
        pilha.getStyleClass().add("raiz");
        Shell.marcarTema(pilha);
        Aviso.instalar(pilha);

        Scene cena = new Scene(pilha, 1100, 720);
        cena.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        // A moldura nativa também trazia o redimensionar pelas bordas.
        RedimensionadorJanela.instalar(janela, cena, 860, 560);

        // Ctrl+L tranca de qualquer lugar do aplicativo.
        cena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                SecurityService::trancar);

        // Ctrl+F leva ao campo de busca da tela aberta, se ela tiver um.
        cena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> CampoBusca.focarNaTela(cena));

        SessionManager.observar(cena);

        janela.setTitle("MyApp");
        janela.getIcons().setAll(IconeApp.paraJanela());
        janela.setScene(cena);
        janela.setMinWidth(860);
        janela.setMinHeight(560);

        // Fechar no X pode apenas esconder, para o aplicativo seguir vigiando.
        janela.setOnCloseRequest(evento -> {
            if (!encerrandoDeVerdade && Config.get().fecharVaiParaBandeja && bandeja != null) {
                evento.consume();
                esconder();
            } else {
                encerrar();
            }
        });

        // O JavaFX encerraria ao fechar a última janela; aqui quem manda e a bandeja.
        Platform.setImplicitExit(false);

        bandeja = new BandejaSistema(this::mostrar, this::encerrar);
        boolean temBandeja = bandeja.instalar();
        if (!temBandeja) {
            // Sem bandeja, fechar precisa encerrar mesmo, senao o app fica invisível.
            Config.get().fecharVaiParaBandeja = false;
            bandeja = null;
        }

        if (!Config.get().iniciarMinimizado || !temBandeja) {
            janela.show();
        }
    }

    private void mostrar() {
        janela.show();
        janela.setIconified(false);
        janela.toFront();
        janela.requestFocus();
        SessionManager.registrarAtividade();
    }

    private void esconder() {
        janela.hide();
        if (Config.get().bloquearAoMinimizar) {
            SecurityService.trancar();
        }
        if (bandeja != null) {
            bandeja.notificar("MyApp continua ativo",
                    "A agenda segue sendo vigiada. Clique duas vezes aqui para reabrir.");
        }
    }

    // ------------------------------------------------------------ Serviços

    private void ligarServicos() {
        // Alerta de lembrete: popup do aplicativo + notificação do Windows.
        EventBus.ouvir(Scheduler.AlertaDisparado.class, alerta -> {
            PopupAlerta.mostrar(alerta);
            if (bandeja != null && !alerta.atrasado()) {
                boolean protegido = alerta.lembrete().isSensivel() && !SecurityService.estaDestrancado();
                bandeja.notificar("MyApp - " + alerta.resumoTempo(),
                        protegido ? "Lembrete protegido" : alerta.lembrete().getTitulo());
            }
        });

        Scheduler.iniciar();
        SessionManager.iniciar();

        if (Config.get().backupAutomatico) {
            BackupService.iniciarAutomatico();
        }

        // Faxina do histórico, uma vez por execução.
        new Thread(() -> {
            try {
                new LembreteService().limpar();
            } catch (Exception e) {
                Log.aviso("Falha na limpeza inicial: " + e.getMessage());
            }
        }, "limpeza-inicial").start();
    }

    /**
     * Primeira abertura pede a criação da senha mestra; as seguintes abrem
     * trancadas.
     */
    private void tratarPrimeiraExecucao() {
        if (!SecurityService.estaConfigurado()) {
            shell.mostrarCadastroDeSenha(() ->
                    Log.info("Configuração inicial concluida."));
        } else {
            shell.mostrarBloqueio();
        }
    }

    // ----------------------------------------------------------- encerramento

    private void encerrar() {
        encerrandoDeVerdade = true;
        Log.info("Encerrando o MyApp.");
        try {
            Scheduler.parar();
            SessionManager.parar();
            BackupService.pararAutomatico();
            SecurityService.trancar();       // apaga a chave da memória
            if (bandeja != null) {
                bandeja.remover();
            }
            Database.fechar();
        } catch (Exception e) {
            Log.aviso("Erro durante o encerramento: " + e.getMessage());
        }
        Platform.exit();
        System.exit(0);
    }

    private void mostrarErroFatal(Exception e) {
        try {
            javafx.scene.control.Alert alerta =
                    new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alerta.setTitle("MyApp");
            alerta.setHeaderText("O aplicativo não conseguiu iniciar");
            alerta.setContentText(e.getMessage() + "\n\nDetalhes no log em:\n"
                    + br.com.myapp.core.AppPaths.pastaLogs());
            alerta.showAndWait();
        } catch (Exception ignorado) {
            // Se nem o alerta abre, o log em disco e o que resta.
        }
        Platform.exit();
        System.exit(1);
    }

    @Override
    public void stop() {
        if (!encerrandoDeVerdade) {
            encerrar();
        }
    }
}
