package br.com.myapp.modules.atalhos;

import br.com.myapp.core.Texto;
import br.com.myapp.modules.AppModule;
import br.com.myapp.ui.Icone;
import javafx.scene.Node;

import java.util.List;

/** Registro do módulo Atalhos. */
public class AtalhosModule implements AppModule {

    private AtalhosView tela;
    private final AtalhoService servico = new AtalhoService();

    @Override
    public String id() {
        return "atalhos";
    }

    @Override
    public String nome() {
        return "Atalhos";
    }

    @Override
    public String descricao() {
        return "Programas, pastas, sites e arquivos do dia a dia, a um clique, separados em grupos.";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.GRADE;
    }

    @Override
    public int ordem() {
        // Depois das notas (20) e antes do Úteis (30): é de uso diário, mas
        // não é registro de trabalho como lembretes e notas.
        return 25;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new AtalhosView();
        }
        return tela;
    }

    @Override
    public void aoExibir() {
        if (tela != null) {
            tela.recarregar();
        }
    }

    @Override
    public List<ResultadoBusca> buscar(String termo) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return servico.grupos().stream()
                .flatMap(g -> g.getAtalhos().stream()
                        .filter(a -> Texto.contem(a.getNome(), termo) || Texto.contem(a.getAlvo(), termo))
                        .map(a -> new ResultadoBusca(id(), a.getNome(), "Atalhos · " + g.getNome(), () -> {
                        })))
                .limit(10)
                .toList();
    }
}
