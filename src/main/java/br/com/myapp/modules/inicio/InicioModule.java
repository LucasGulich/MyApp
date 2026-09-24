package br.com.myapp.modules.inicio;

import br.com.myapp.modules.AppModule;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.scene.Node;

/**
 * Registro da tela inicial.
 *
 * Ordem 0: é a primeira do menu e a que abre depois da senha.
 */
public class InicioModule implements AppModule {

    private InicioView tela;

    @Override
    public String id() {
        return "inicio";
    }

    @Override
    public String nome() {
        return "Início";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.INICIO;
    }

    @Override
    public int ordem() {
        return 0;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new InicioView();
        }
        return tela;
    }

    @Override
    public boolean exigeDesbloqueio() {
        // A tela em si não guarda segredo. O que é protegido — o título de um
        // lembrete marcado — já se esconde sozinho na agenda do dia.
        return false;
    }

    @Override
    public void aoExibir() {
        if (tela != null) {
            // A agenda depende da hora: reabrir a tela precisa recalcular o
            // que já passou e o que ainda vem.
            tela.recarregar();
        }
    }

    @Override
    public void aoMudarBloqueio(boolean destrancado) {
        if (tela != null) {
            Platform.runLater(tela::recarregar);
        }
    }
}
