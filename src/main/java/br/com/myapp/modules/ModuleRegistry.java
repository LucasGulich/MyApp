package br.com.myapp.modules;

import br.com.myapp.core.Log;
import br.com.myapp.modules.inicio.InicioModule;
import br.com.myapp.modules.kanban.KanbanModule;
import br.com.myapp.modules.lembretes.LembretesModule;
import br.com.myapp.modules.notas.NotasModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lista dos módulos ativos.
 *
 * Este e o único ponto do código que precisa ser tocado para acrescentar uma
 * área nova ao aplicativo. Escreva a classe do módulo e registre-a em
 * {@link #registrarPadroes()}.
 */
public final class ModuleRegistry {

    private static final List<AppModule> MODULOS = new ArrayList<>();

    private ModuleRegistry() {
    }

    /**
     * Módulos que sobem junto com o aplicativo.
     *
     * Roteiro já previsto para as próximas versões - basta descomentar
     * conforme cada um for escrito:
     *
     *   registrar(new TarefasModule());       // lista de tarefas
     *   registrar(new KanbanModule());        // quadro visual
     *   registrar(new FerramentasModule());   // JSON, base64, hash, datas
     *   registrar(new AtendimentosModule());  // diário de chamados
     */
    public static void registrarPadroes() {
        registrar(new InicioModule());
        registrar(new LembretesModule());
        registrar(new KanbanModule());
        registrar(new NotasModule());
    }

    public static void registrar(AppModule modulo) {
        MODULOS.add(modulo);
        MODULOS.sort(Comparator.comparingInt(AppModule::ordem).thenComparing(AppModule::nome));
        Log.info("Módulo registrado: " + modulo.id());
    }

    public static List<AppModule> todos() {
        return List.copyOf(MODULOS);
    }

    public static Optional<AppModule> porId(String id) {
        return MODULOS.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Avisa todos os módulos que o estado de bloqueio mudou. */
    public static void notificarBloqueio(boolean destrancado) {
        for (AppModule m : MODULOS) {
            try {
                m.aoMudarBloqueio(destrancado);
            } catch (Exception e) {
                Log.erro("Módulo " + m.id() + " falhou ao tratar a mudança de bloqueio", e);
            }
        }
    }
}
