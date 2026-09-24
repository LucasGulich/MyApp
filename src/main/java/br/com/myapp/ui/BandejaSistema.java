package br.com.myapp.ui;

import br.com.myapp.core.Log;
import br.com.myapp.security.SecurityService;
import javafx.application.Platform;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/**
 * ícone ao lado do relógio do Windows.
 *
 * E o que permite o aplicativo viver minimizado vigiando a agenda, como o
 * Lembrete.bat fazia deixando tudo por conta do Agendador de Tarefas. Clique
 * duplo abre a janela; o menu do botao direito da acesso rápido a trancar e
 * sair.
 */
public class BandejaSistema {

    private TrayIcon icone;
    private final Runnable aoAbrir;
    private final Runnable aoSair;

    public BandejaSistema(Runnable aoAbrir, Runnable aoSair) {
        this.aoAbrir = aoAbrir;
        this.aoSair = aoSair;
    }

    /** Instala o ícone. Devolve false se o Windows não oferecer a bandeja. */
    public boolean instalar() {
        if (!SystemTray.isSupported()) {
            Log.aviso("Este sistema não oferece área de notificação.");
            return false;
        }
        try {
            icone = new TrayIcon(desenharIcone(), "MyApp", montarMenu());
            icone.setImageAutoSize(true);
            icone.addActionListener(e -> Platform.runLater(aoAbrir));   // clique duplo
            SystemTray.getSystemTray().add(icone);
            return true;
        } catch (AWTException e) {
            Log.erro("Falha ao instalar o ícone na bandeja", e);
            return false;
        }
    }

    private PopupMenu montarMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem abrir = new MenuItem("Abrir MyApp");
        abrir.addActionListener(e -> Platform.runLater(aoAbrir));

        MenuItem trancar = new MenuItem("Trancar agora");
        trancar.addActionListener(e -> Platform.runLater(() -> {
            SecurityService.trancar();
            aoAbrir.run();   // mostra a tela de bloqueio
        }));

        MenuItem sair = new MenuItem("Sair");
        sair.addActionListener(e -> Platform.runLater(aoSair));

        menu.add(abrir);
        menu.add(trancar);
        menu.addSeparator();
        menu.add(sair);
        return menu;
    }

    /** Notificação nativa do Windows, usada junto com o alerta do aplicativo. */
    public void notificar(String titulo, String mensagem) {
        if (icone != null) {
            try {
                icone.displayMessage(titulo, mensagem, TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                Log.aviso("Falha ao exibir notificação do sistema: " + e.getMessage());
            }
        }
    }

    public void remover() {
        if (icone != null) {
            SystemTray.getSystemTray().remove(icone);
            icone = null;
        }
    }

    /**
     * Desenha o ícone em tempo de execução.
     *
     * Evita depender de um arquivo .png externo, que precisaria ser empacotado
     * e poderia sumir. Um sino simples, em azul.
     */
    /**
     * O ícone da bandeja.
     *
     * Pede ao Windows o tamanho que ele realmente vai usar, em vez de fixar
     * 32 px e deixar o sistema redimensionar — o resultado fica nítido também
     * em telas com escala de 125% ou 150%.
     */
    private Image desenharIcone() {
        java.awt.Dimension pedido = SystemTray.getSystemTray().getTrayIconSize();
        int lado = Math.max(16, Math.min(pedido.width, pedido.height));
        return IconeApp.paraBandeja(lado);
    }

    /** Aplica a ação a bandeja apenas se ela existir. */
    public void seInstalada(Consumer<BandejaSistema> acao) {
        if (icone != null) {
            acao.accept(this);
        }
    }
}
