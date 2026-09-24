package br.com.myapp.modules;

import br.com.myapp.ui.Icone;
import javafx.scene.Node;

import java.util.List;

/**
 * Contrato de um módulo do aplicativo.
 *
 * Esta interface e o que torna o MyApp expansivel. Cada área (lembretes,
 * tarefas, notas, cofre, kanban, ferramentas...) e um módulo independente.
 * Para acrescentar uma área nova você escreve uma classe que implementa esta
 * interface e registra em {@link ModuleRegistry}. O restante do aplicativo -
 * menu lateral, bloqueio, busca - passa a atende-la sem nenhuma alteração.
 */
public interface AppModule {

    /** Identificador curto e estável, usado em configurações. Ex.: "lembretes". */
    String id();

    /** Nome exibido no menu lateral. */
    String nome();

    /**
     * Ícone do menu, escolhido no catálogo vetorial do aplicativo.
     *
     * É um símbolo e não um texto porque o desenho precisa acompanhar o tema
     * e o estado do item: um emoji ficaria sempre da mesma cor, tanto no item
     * ativo quanto no inativo, e some de legibilidade em tamanho pequeno.
     */
    Icone.Simbolo icone();

    /** Ordem no menu lateral. Menor aparece antes. */
    default int ordem() {
        return 100;
    }

    /**
     * Monta a tela do módulo.
     *
     * Chamado uma única vez, na primeira abertura. O resultado fica em cache
     * para que o estado da tela (filtros, rolagem) sobreviva a troca de aba.
     */
    Node criarTela();

    /**
     * Este módulo exige o aplicativo destrancado para abrir?
     *
     * Lembretes respondem não: a agenda continua útil com o app trancado, e o
     * conteúdo protegido de cada item já e escondido individualmente. Um cofre
     * de senhas responderia sim.
     */
    default boolean exigeDesbloqueio() {
        return false;
    }

    /** Chamado quando o módulo volta a ficar visível, para recarregar dados. */
    default void aoExibir() {
    }

    /** Chamado quando o aplicativo tranca ou destranca, para redesenhar o que for preciso. */
    default void aoMudarBloqueio(boolean destrancado) {
    }

    /**
     * Resultados deste módulo para a busca global.
     *
     * Ainda não ha tela de busca; o método já existe para que os módulos
     * escritos hoje funcionem nela sem alteração quando ela chegar.
     */
    default List<ResultadoBusca> buscar(String termo) {
        return List.of();
    }

    /** Uma linha de resultado na busca global. */
    record ResultadoBusca(String moduloId, String titulo, String detalhe, Runnable aoAbrir) {
    }
}
