package br.com.myapp.modules.lembretes;

import br.com.myapp.core.Texto;
import br.com.myapp.modules.AppModule;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.scene.Node;

import java.util.List;

/**
 * Registro do módulo de lembretes.
 *
 * Esta classe e proposital e deliberadamente pequena: e o exemplo vivo de como
 * acrescentar uma área nova ao aplicativo. Um módulo de tarefas, de notas ou o
 * kanban seguem exatamente este formato.
 */
public class LembretesModule implements AppModule {

    private LembretesView tela;
    private final LembreteService servico = new LembreteService();

    @Override
    public String id() {
        return "lembretes";
    }

    @Override
    public String nome() {
        return "Lembretes";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.LEMBRETE;
    }

    @Override
    public int ordem() {
        return 10;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new LembretesView();
        }
        return tela;
    }

    @Override
    public boolean exigeDesbloqueio() {
        // A agenda continua útil com o app trancado: os horários aparecem e os
        // avisos saem. O conteúdo de cada item protegido e escondido a parte.
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
        // Ao destrancar, os títulos protegidos passam a ser legíveis: redesenha.
        if (tela != null) {
            Platform.runLater(tela::recarregar);
        }
    }

    @Override
    public List<ResultadoBusca> buscar(String termo) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return servico.listarTodos().stream()
                // Item protegido com o app trancado não entra na busca.
                .filter(l -> !l.isSensivel() || SecurityService.estaDestrancado())
                .filter(l -> Texto.contem(l.getTitulo(), termo)
                        || Texto.contem(l.getDescricao(), termo))
                .limit(10)
                .map(l -> new ResultadoBusca(id(), l.getTitulo(), l.resumoRecorrencia(), () -> {
                }))
                .toList();
    }
}
