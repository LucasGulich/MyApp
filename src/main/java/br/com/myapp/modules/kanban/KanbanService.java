package br.com.myapp.modules.kanban;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;

import java.util.List;

/**
 * Regras de negócio do quadro Kanban.
 *
 * <p>A tela conversa com esta classe, nunca direto com o banco — o mesmo
 * arranjo dos outros módulos. Assim a regra vale para qualquer origem, e a
 * tela fica só com o desenho.
 */
public class KanbanService {

    /** Publicado quando o quadro muda, para as telas se redesenharem. */
    public record QuadroMudou() {
    }

    /** Não faz sentido um quadro com dezenas de colunas: ninguém enxerga. */
    public static final int MAXIMO_DE_COLUNAS = 20;

    private final KanbanDao dao = new KanbanDao();

    // --------------------------------------------------------------- leitura

    /** O quadro inteiro, colunas na ordem, cada uma com os seus cartões. */
    public List<ColunaKanban> quadro() {
        return dao.quadro();
    }

    /** Há alguma coluna criada? Define se a tela mostra o estado vazio. */
    public boolean vazio() {
        return dao.quadro().isEmpty();
    }

    // --------------------------------------------------------------- colunas

    /**
     * Grava a coluna depois de validar.
     *
     * @throws IllegalArgumentException com a mensagem pronta para a tela
     */
    public ColunaKanban salvarColuna(ColunaKanban coluna) {
        String erro = coluna.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (coluna.getId() == null && dao.quadro().size() >= MAXIMO_DE_COLUNAS) {
            throw new IllegalArgumentException(
                    "O quadro já tem " + MAXIMO_DE_COLUNAS + " colunas. "
                            + "Junte ou remova alguma antes de criar outra.");
        }
        ColunaKanban salva = dao.salvarColuna(coluna);
        EventBus.publicar(new QuadroMudou());
        return salva;
    }

    /** Exclui a coluna e os cartões dela — logicamente, como tudo aqui. */
    public void excluirColuna(long id) {
        dao.excluirColuna(id);
        Log.info("Coluna " + id + " excluida (logicamente), com os cartoes dela.");
        EventBus.publicar(new QuadroMudou());
    }

    public void reordenarColunas(List<ColunaKanban> naNovaOrdem) {
        dao.gravarOrdemDasColunas(naNovaOrdem);
        EventBus.publicar(new QuadroMudou());
    }

    // --------------------------------------------------------------- cartões

    /**
     * Grava o cartão depois de validar.
     *
     * @throws IllegalArgumentException com a mensagem pronta para a tela
     * @throws IllegalStateException    se for protegido e o app estiver trancado
     */
    public CardKanban salvarCard(CardKanban card) {
        String erro = card.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (card.getColunaId() == null) {
            throw new IllegalArgumentException("O cartão precisa pertencer a uma coluna.");
        }
        CardKanban salvo = dao.salvarCard(card);
        EventBus.publicar(new QuadroMudou());
        return salvo;
    }

    public void excluirCard(long id) {
        dao.excluirCard(id);
        EventBus.publicar(new QuadroMudou());
    }

    /** Duplica um cartão dentro da mesma coluna. */
    public CardKanban duplicar(CardKanban original) {
        return salvarCard(original.copia());
    }

    /**
     * Arquiva ou desarquiva.
     *
     * <p>Arquivar tira da vista sem tirar do quadro. É o gesto para o que já
     * foi concluído: excluir seria dizer que não devia existir.
     */
    public void alternarArquivado(CardKanban card) {
        dao.definirArquivado(card.getId(), !card.isArquivado());
        card.setArquivado(!card.isArquivado());
        EventBus.publicar(new QuadroMudou());
    }

    /**
     * Reordena os cartões de uma coluna.
     *
     * <p>Serve também para o cartão que mudou de coluna: a lista recebida é o
     * conteúdo final da coluna de destino, na ordem final.
     */
    public void reordenarCards(long colunaId, List<CardKanban> naNovaOrdem) {
        dao.gravarOrdemDosCards(colunaId, naNovaOrdem);
        EventBus.publicar(new QuadroMudou());
    }

    /**
     * Move um cartão para junto de outro, dentro da mesma coluna ou de outra.
     *
     * <p>A posição é dada por um cartão de referência e um lado, e não por um
     * índice: quem arrasta aponta para um vizinho, não para um número. E
     * resolver o índice aqui, <b>depois</b> de tirar o cartão da origem, evita
     * o erro clássico de quem calcula antes — na mesma coluna, arrastar para
     * baixo cairia sempre uma posição adiante do pretendido.
     *
     * <p>Grava as duas colunas afetadas: a de origem precisa fechar o buraco
     * que o cartão deixou, senão as posições dela ficam com um salto e o
     * arrasto seguinte cai no lugar errado.
     *
     * @param alvo  o cartão de referência, ou nulo para o fim da coluna
     * @param antes se o cartão entra antes ou depois do alvo
     */
    public void moverCard(CardKanban card, ColunaKanban origem, ColunaKanban destino,
                          CardKanban alvo, boolean antes) {
        origem.getCards().removeIf(c -> c.getId().equals(card.getId()));

        List<CardKanban> chegada = destino.getCards();
        int onde = chegada.size();
        if (alvo != null) {
            int posicaoDoAlvo = chegada.indexOf(alvo);
            if (posicaoDoAlvo >= 0) {
                onde = antes ? posicaoDoAlvo : posicaoDoAlvo + 1;
            }
        }
        chegada.add(Math.max(0, Math.min(onde, chegada.size())), card);

        dao.gravarOrdemDosCards(destino.getId(), chegada);
        if (!origem.getId().equals(destino.getId())) {
            dao.gravarOrdemDosCards(origem.getId(), origem.getCards());
        }
        EventBus.publicar(new QuadroMudou());
    }

    /** Cria as colunas de exemplo na primeira abertura do módulo. */
    public void criarColunasIniciaisSeVazio() {
        if (!dao.quadro().isEmpty()) {
            return;
        }
        criar("A fazer", CorKanban.AZUL);
        criar("Em andamento", CorKanban.AMBAR);
        criar("Concluído", CorKanban.VERDE);
        Log.info("Colunas iniciais do Kanban criadas.");
    }

    private void criar(String nome, CorKanban cor) {
        ColunaKanban c = new ColunaKanban();
        c.setNome(nome);
        c.setCor(cor);
        dao.salvarColuna(c);
    }
}
