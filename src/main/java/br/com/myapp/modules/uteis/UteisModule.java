package br.com.myapp.modules.uteis;

import br.com.myapp.modules.AppModule;
import br.com.myapp.ui.Icone;
import javafx.scene.Node;

/**
 * Registro do módulo Úteis.
 *
 * <p>A casa das pequenas ferramentas do dia a dia. Por enquanto só o lugar
 * existe: o conteúdo entra aos poucos, cada ferramenta como um bloco da tela.
 */
public class UteisModule implements AppModule {

    private UteisView tela;

    @Override
    public String id() {
        return "uteis";
    }

    @Override
    public String nome() {
        return "Úteis";
    }

    @Override
    public String descricao() {
        return "Pequenas ferramentas do dia a dia, sem precisar abrir um site.";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.FERRAMENTA;
    }

    @Override
    public int ordem() {
        // Depois das notas (20): é apoio, não registro do trabalho.
        return 30;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new UteisView();
        }
        return tela;
    }
}
