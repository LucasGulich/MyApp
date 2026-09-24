package br.com.myapp.modules.kanban;

import br.com.myapp.core.Texto;
import br.com.myapp.modules.AppModule;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.scene.Node;

import java.util.List;

/**
 * Registro do módulo Kanban.
 *
 * <p>Como os demais, é uma classe pequena: a ligação com o aplicativo cabe em
 * meia dúzia de métodos, e menu, bloqueio, criptografia e backup vêm do
 * núcleo sem precisar ser pedidos.
 */
public class KanbanModule implements AppModule {

    private KanbanView tela;
    private final KanbanService servico = new KanbanService();

    @Override
    public String id() {
        return "kanban";
    }

    @Override
    public String nome() {
        return "Kanban";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.QUADRO;
    }

    @Override
    public int ordem() {
        // Entre lembretes (10) e notas (20): o quadro é sobre o que está em
        // andamento, e isso fica mais perto da agenda do que do arquivo.
        return 15;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new KanbanView();
        }
        return tela;
    }

    @Override
    public boolean exigeDesbloqueio() {
        // Falso pelo mesmo motivo das notas: um cartão comum continua útil com
        // o aplicativo trancado, e o que é sensível já se esconde sozinho.
        return false;
    }

    @Override
    public void aoExibir() {
        if (tela != null) {
            tela.recarregar();
        }
    }

    @Override
    public void aoMudarBloqueio(boolean destrancado) {
        if (tela != null) {
            Platform.runLater(tela::aoMudarBloqueio);
        }
    }

    @Override
    public List<ResultadoBusca> buscar(String termo) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return servico.quadro().stream()
                .flatMap(coluna -> coluna.getCards().stream()
                        .filter(card -> !card.isProtegido() || SecurityService.estaDestrancado())
                        .filter(card -> Texto.contem(card.getTitulo(), termo)
                                || Texto.contem(card.getDescricao(), termo))
                        .map(card -> new ResultadoBusca(
                                id(), card.getTitulo(), "Kanban · " + coluna.getNome(), () -> {
                        })))
                .limit(10)
                .toList();
    }
}
