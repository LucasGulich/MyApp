package br.com.myapp.modules.notas;

import br.com.myapp.modules.AppModule;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.scene.Node;

import java.util.List;

/**
 * Registro do módulo de notas.
 *
 * Como o de lembretes, é uma classe pequena: toda a ligação com o aplicativo
 * cabe em meia dúzia de métodos, e o resto — menu, bloqueio, criptografia,
 * backup — vem do núcleo sem precisar ser pedido.
 */
public class NotasModule implements AppModule {

    private NotasView tela;
    private final NotaService servico = new NotaService();

    @Override
    public String id() {
        return "notas";
    }

    @Override
    public String nome() {
        return "Notas";
    }

    @Override
    public Icone.Simbolo icone() {
        return Icone.Simbolo.NOTA;
    }

    @Override
    public int ordem() {
        return 20;
    }

    @Override
    public Node criarTela() {
        if (tela == null) {
            tela = new NotasView();
        }
        return tela;
    }

    @Override
    public boolean exigeDesbloqueio() {
        // Falso de propósito: uma nota comum continua útil com o app trancado.
        // O que é protegido já se esconde item a item, e uma categoria pode
        // ser marcada para sumir por inteiro.
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
        return servico.buscar(termo, servico.listarTodas()).stream()
                // Nota protegida com o app trancado fica de fora da busca.
                .filter(n -> !n.isProtegida() || SecurityService.estaDestrancado())
                .limit(10)
                .map(n -> new ResultadoBusca(id(), n.getTitulo(), n.resumo(), () -> {
                }))
                .toList();
    }
}
