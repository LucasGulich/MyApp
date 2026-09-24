package br.com.myapp;

import br.com.myapp.core.Log;
import br.com.myapp.core.WindowsIntegracao;
import javafx.application.Application;

/**
 * Ponto de entrada do MyApp.
 *
 * Esta classe existe separada de {@link App} por um detalhe do JavaFX: quando
 * a classe principal estende Application, a JVM exige o JavaFX no module-path
 * e recusa a inicialização no classpath comum. Com um Main que apenas chama
 * Application.launch, o aplicativo roda tanto pelo Maven quanto pelo .exe
 * gerado pelo jpackage, sem configuração extra.
 */
public final class Main {

    public static void main(String[] args) {
        // Uma única instância: duas disputariam o banco e duplicariam os avisos.
        if (!WindowsIntegracao.garantirInstanciaUnica()) {
            System.out.println("O MyApp já está aberto. Procure o ícone ao lado do relógio.");
            javax.swing.JOptionPane.showMessageDialog(null,
                    "O MyApp já está aberto.\nProcure o ícone ao lado do relógio do Windows.",
                    "MyApp", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Qualquer erro não tratado vai para o log, em vez de sumir no console.
        Thread.setDefaultUncaughtExceptionHandler((thread, erro) ->
                Log.erro("Erro não tratado na thread " + thread.getName(), erro));

        Log.info("Iniciando o MyApp...");
        Application.launch(App.class, args);
    }

    private Main() {
    }
}
