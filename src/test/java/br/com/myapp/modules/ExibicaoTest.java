package br.com.myapp.modules;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.ui.Icone;
import javafx.scene.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configurações &gt; Exibição: quais módulos ficam no menu e se o mural de
 * recados aparece na tela inicial.
 */
class ExibicaoTest {

    @TempDir
    Path pastaTemporaria;

    @BeforeEach
    void preparar() {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
    }

    @AfterEach
    void limpar() {
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    @Test
    @DisplayName("Tudo aparece de saída, inclusive um módulo que acabou de chegar")
    void tudoVisivelPorPadrao() {
        assertTrue(ModuleRegistry.estaVisivel(modulo("kanban", true)));
        assertTrue(ModuleRegistry.estaVisivel(modulo("uteis", true)));
        assertTrue(Config.get().mostrarRecados);
        assertTrue(Config.get().mostrarAreaTransferencia);
        assertTrue(Config.get().mostrarGeradorSenha);
        assertTrue(Config.get().mostrarYoutube);
    }

    @Test
    @DisplayName("Módulo oculto sai do menu; o que não pode ser ocultado fica mesmo listado")
    void ocultarRespeitaPodeOcultar() {
        Config.get().modulosOcultos = Set.of("kanban", "inicio");

        assertFalse(ModuleRegistry.estaVisivel(modulo("kanban", true)));
        assertTrue(ModuleRegistry.estaVisivel(modulo("notas", true)));
        // Um config.json editado à mão não consegue esconder o início.
        assertTrue(ModuleRegistry.estaVisivel(modulo("inicio", false)));
    }

    @Test
    @DisplayName("A escolha sobrevive a fechar e abrir o app")
    void escolhaFicaGravada() {
        Config.get().modulosOcultos = Set.of("notas");
        Config.get().mostrarRecados = false;
        Config.get().mostrarYoutube = false;
        Config.get().salvar();

        Config.redefinir();

        assertTrue(Config.get().modulosOcultos.contains("notas"));
        assertFalse(Config.get().mostrarRecados);
        assertFalse(Config.get().mostrarYoutube);
        assertTrue(Config.get().mostrarAreaTransferencia);
        assertFalse(ModuleRegistry.estaVisivel(modulo("notas", true)));
    }

    private static AppModule modulo(String id, boolean podeOcultar) {
        return new AppModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String nome() {
                return id;
            }

            @Override
            public Icone.Simbolo icone() {
                return Icone.Simbolo.NOTA;
            }

            @Override
            public boolean podeOcultar() {
                return podeOcultar;
            }

            @Override
            public Node criarTela() {
                return null;
            }
        };
    }
}
