package br.com.myapp.modules;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.modules.atalhos.AtalhosModule;
import br.com.myapp.modules.inicio.InicioModule;
import br.com.myapp.modules.kanban.KanbanModule;
import br.com.myapp.modules.lembretes.LembretesModule;
import br.com.myapp.modules.notas.NotasModule;
import br.com.myapp.modules.uteis.UteisModule;

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
     *   registrar(new AtendimentosModule());  // diário de chamados
     */
    public static void registrarPadroes() {
        registrar(new InicioModule());
        registrar(new LembretesModule());
        registrar(new KanbanModule());
        registrar(new NotasModule());
        registrar(new AtalhosModule());
        registrar(new UteisModule());
    }

    public static void registrar(AppModule modulo) {
        MODULOS.add(modulo);
        MODULOS.sort(Comparator.comparingInt(AppModule::ordem).thenComparing(AppModule::nome));
        Log.info("Módulo registrado: " + modulo.id());
    }

    public static List<AppModule> todos() {
        return List.copyOf(MODULOS);
    }

    /**
     * Está no menu lateral?
     *
     * Quem não pode ser ocultado responde sim mesmo que o id tenha ido parar
     * na lista de ocultos — por um config.json editado à mão, por exemplo.
     */
    public static boolean estaVisivel(AppModule modulo) {
        return !modulo.podeOcultar() || !Config.get().modulosOcultos.contains(modulo.id());
    }

    public static boolean estaVisivel(String id) {
        return porId(id).map(ModuleRegistry::estaVisivel).orElse(false);
    }

    /** Os módulos que aparecem no menu, na ordem do menu. */
    public static List<AppModule> visiveis() {
        return MODULOS.stream().filter(ModuleRegistry::estaVisivel).toList();
    }

    /**
     * Publicado quando muda o que aparece na tela: módulos do menu ou blocos
     * opcionais de uma tela, como o mural de recados. Quem desenha escuta e
     * se ajusta na hora, sem precisar reabrir o app.
     */
    public record ExibicaoMudou() {
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
