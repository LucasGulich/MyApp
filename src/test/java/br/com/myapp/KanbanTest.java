package br.com.myapp;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.data.Database;
import br.com.myapp.modules.kanban.CardKanban;
import br.com.myapp.modules.kanban.ColunaKanban;
import br.com.myapp.modules.kanban.CorKanban;
import br.com.myapp.modules.kanban.KanbanDao;
import br.com.myapp.modules.kanban.KanbanService;
import br.com.myapp.security.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do quadro Kanban.
 *
 * O foco está no que é fácil de quebrar sem perceber: a ordem depois de
 * arrastar — que é onde mora o erro clássico de índice —, a cascata ao
 * excluir uma coluna, a diferença entre arquivar e excluir, e a promessa de
 * que nada sai do banco de verdade.
 */
class KanbanTest {

    @TempDir
    Path pastaTemporaria;

    private KanbanService servico;

    @BeforeEach
    void preparar() {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        Database.fechar();
        Database.conexao();

        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        servico = new KanbanService();
    }

    @AfterEach
    void limpar() {
        SecurityService.trancar();
        Database.fechar();
        SecurityService.redefinir();
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    // ------------------------------------------------------------- básico

    @Test
    @DisplayName("Uma coluna nova entra no fim do quadro")
    void colunaNovaVaiParaOFim() {
        criarColuna("A fazer");
        criarColuna("Em andamento");
        ColunaKanban ultima = criarColuna("Concluído");

        List<ColunaKanban> quadro = servico.quadro();
        assertEquals(3, quadro.size());
        assertEquals(ultima.getId(), quadro.get(2).getId());
    }

    @Test
    @DisplayName("Coluna sem nome não é aceita")
    void colunaPrecisaDeNome() {
        ColunaKanban vazia = new ColunaKanban();
        vazia.setNome("   ");
        assertThrows(IllegalArgumentException.class, () -> servico.salvarColuna(vazia));
    }

    @Test
    @DisplayName("Cartão sem título não é aceito")
    void cardPrecisaDeTitulo() {
        ColunaKanban coluna = criarColuna("A fazer");
        CardKanban semTitulo = new CardKanban();
        semTitulo.setColunaId(coluna.getId());
        semTitulo.setTitulo("");
        assertThrows(IllegalArgumentException.class, () -> servico.salvarCard(semTitulo));
    }

    @Test
    @DisplayName("O quadro respeita o limite de colunas")
    void limiteDeColunas() {
        for (int i = 0; i < KanbanService.MAXIMO_DE_COLUNAS; i++) {
            criarColuna("Coluna " + i);
        }
        ColunaKanban aMais = new ColunaKanban();
        aMais.setNome("Uma a mais");
        assertThrows(IllegalArgumentException.class, () -> servico.salvarColuna(aMais));
    }

    // -------------------------------------------------------------- ordem

    @Test
    @DisplayName("Arrastar um cartão para baixo na mesma coluna cai na posição certa")
    void reordenarDentroDaColuna() {
        ColunaKanban coluna = criarColuna("A fazer");
        criarCard(coluna, "primeiro");
        criarCard(coluna, "segundo");
        criarCard(coluna, "terceiro");

        ColunaKanban lida = servico.quadro().get(0);
        CardKanban primeiro = lida.getCards().get(0);
        CardKanban terceiro = lida.getCards().get(2);

        // Solta o primeiro depois do terceiro: deve virar o último. É aqui que
        // um cálculo de índice feito antes da remoção erraria por um.
        servico.moverCard(primeiro, lida, lida, terceiro, false);

        List<CardKanban> depois = servico.quadro().get(0).getCards();
        assertEquals(List.of("segundo", "terceiro", "primeiro"), titulos(depois));
    }

    @Test
    @DisplayName("Arrastar um cartão para cima na mesma coluna cai na posição certa")
    void reordenarParaCima() {
        ColunaKanban coluna = criarColuna("A fazer");
        criarCard(coluna, "primeiro");
        criarCard(coluna, "segundo");
        criarCard(coluna, "terceiro");

        ColunaKanban lida = servico.quadro().get(0);
        CardKanban terceiro = lida.getCards().get(2);
        CardKanban primeiro = lida.getCards().get(0);

        servico.moverCard(terceiro, lida, lida, primeiro, true);

        assertEquals(List.of("terceiro", "primeiro", "segundo"),
                titulos(servico.quadro().get(0).getCards()));
    }

    @Test
    @DisplayName("Mover um cartão de coluna fecha o buraco na coluna de origem")
    void moverEntreColunas() {
        ColunaKanban origem = criarColuna("A fazer");
        ColunaKanban destino = criarColuna("Concluído");
        criarCard(origem, "a");
        criarCard(origem, "b");
        criarCard(origem, "c");

        List<ColunaKanban> quadro = servico.quadro();
        ColunaKanban de = quadro.get(0);
        ColunaKanban para = quadro.get(1);
        CardKanban meio = de.getCards().get(1);

        servico.moverCard(meio, de, para, null, false);

        List<ColunaKanban> depois = servico.quadro();
        assertEquals(List.of("a", "c"), titulos(depois.get(0).getCards()));
        assertEquals(List.of("b"), titulos(depois.get(1).getCards()));

        // As posições da origem não podem ficar com salto: 0 e 1, não 0 e 2.
        assertEquals(0, depois.get(0).getCards().get(0).getOrdem());
        assertEquals(1, depois.get(0).getCards().get(1).getOrdem());
        assertEquals(destino.getId(), depois.get(1).getCards().get(0).getColunaId());
    }

    @Test
    @DisplayName("Reordenar colunas grava a ordem nova")
    void reordenarColunas() {
        criarColuna("primeira");
        criarColuna("segunda");
        criarColuna("terceira");

        List<ColunaKanban> quadro = new java.util.ArrayList<>(servico.quadro());
        ColunaKanban ultima = quadro.remove(2);
        quadro.add(0, ultima);
        servico.reordenarColunas(quadro);

        assertEquals(List.of("terceira", "primeira", "segunda"),
                servico.quadro().stream().map(ColunaKanban::getNome).toList());
    }

    // --------------------------------------------------- arquivar/excluir

    @Test
    @DisplayName("Arquivar tira da vista sem tirar do quadro")
    void arquivarNaoExclui() {
        ColunaKanban coluna = criarColuna("A fazer");
        criarCard(coluna, "tarefa");

        CardKanban card = servico.quadro().get(0).getCards().get(0);
        servico.alternarArquivado(card);

        ColunaKanban lida = servico.quadro().get(0);
        assertEquals(1, lida.getCards().size(), "continua na coluna");
        assertEquals(0, lida.visiveis(false).size(), "some da vista");
        assertEquals(1, lida.visiveis(true).size(), "volta ao mostrar arquivados");
        assertEquals(1, lida.quantosArquivados());
    }

    @Test
    @DisplayName("Excluir a coluna leva os cartões dela junto")
    void excluirColunaLevaOsCards() throws Exception {
        ColunaKanban coluna = criarColuna("A fazer");
        criarCard(coluna, "a");
        criarCard(coluna, "b");

        servico.excluirColuna(coluna.getId());

        assertTrue(servico.quadro().isEmpty(), "a coluna some do quadro");
        assertEquals(2, contarNoBanco("SELECT COUNT(*) FROM kanban_card"),
                "os cartões continuam no banco");
        assertEquals(2, contarNoBanco(
                        "SELECT COUNT(*) FROM kanban_card WHERE data_exclusao IS NOT NULL"),
                "e ficaram marcados como excluídos");
    }

    @Test
    @DisplayName("Nada é apagado do banco de verdade")
    void exclusaoEhSempreLogica() throws Exception {
        ColunaKanban coluna = criarColuna("A fazer");
        criarCard(coluna, "some da vista");

        CardKanban card = servico.quadro().get(0).getCards().get(0);
        servico.excluirCard(card.getId());

        assertTrue(servico.quadro().get(0).getCards().isEmpty());
        assertEquals(1, contarNoBanco("SELECT COUNT(*) FROM kanban_card"));
    }

    // ----------------------------------------------------------- proteção

    @Test
    @DisplayName("O texto de um cartão protegido não vai em claro para o banco")
    void cardProtegidoEhCifrado() throws Exception {
        ColunaKanban coluna = criarColuna("A fazer");

        CardKanban card = new CardKanban();
        card.setColunaId(coluna.getId());
        card.setTitulo("Senha do roteador da matriz");
        card.setDescricao("fica atrás do rack");
        card.setProtegido(true);
        servico.salvarCard(card);

        String noBanco = textoNoBanco("SELECT titulo FROM kanban_card WHERE id = " + card.getId());
        assertNotEquals("Senha do roteador da matriz", noBanco);
        assertTrue(noBanco.startsWith("enc:"), "deveria estar cifrado, veio: " + noBanco);
    }

    @Test
    @DisplayName("Com o aplicativo trancado, o cartão protegido aparece sem o texto")
    void cardProtegidoComAppTrancado() {
        ColunaKanban coluna = criarColuna("A fazer");

        CardKanban card = new CardKanban();
        card.setColunaId(coluna.getId());
        card.setTitulo("Assunto reservado");
        card.setProtegido(true);
        servico.salvarCard(card);

        SecurityService.trancar();

        CardKanban lido = servico.quadro().get(0).getCards().get(0);
        assertEquals(KanbanDao.PROTEGIDO, lido.getTitulo());
        assertTrue(lido.isProtegido());
    }

    // -------------------------------------------------------------- prazo

    @Test
    @DisplayName("O prazo distingue vencido, hoje e futuro")
    void prazos() {
        CardKanban ontem = new CardKanban();
        ontem.setPrazo(LocalDate.now().minusDays(1));
        assertTrue(ontem.vencido());
        assertFalse(ontem.venceHoje());

        CardKanban hoje = new CardKanban();
        hoje.setPrazo(LocalDate.now());
        assertFalse(hoje.vencido(), "hoje ainda não está vencido");
        assertTrue(hoje.venceHoje());

        CardKanban amanha = new CardKanban();
        amanha.setPrazo(LocalDate.now().plusDays(1));
        assertFalse(amanha.vencido());
        assertFalse(amanha.venceHoje());
    }

    @Test
    @DisplayName("O prazo sobrevive à ida e volta do banco")
    void prazoPersiste() {
        ColunaKanban coluna = criarColuna("A fazer");
        LocalDate prazo = LocalDate.of(2026, 12, 31);

        CardKanban card = new CardKanban();
        card.setColunaId(coluna.getId());
        card.setTitulo("com prazo");
        card.setPrazo(prazo);
        servico.salvarCard(card);

        assertEquals(prazo, servico.quadro().get(0).getCards().get(0).getPrazo());
    }

    // --------------------------------------------------------------- cor

    @Test
    @DisplayName("Cor desconhecida no banco vira 'sem cor', e não quebra o quadro")
    void corDesconhecidaNaoQuebra() {
        assertEquals(CorKanban.NENHUMA, CorKanban.de("ARCO_IRIS"));
        assertEquals(CorKanban.NENHUMA, CorKanban.de(null));
        assertEquals(CorKanban.AZUL, CorKanban.de("azul"));
        assertEquals("AZUL", CorKanban.AZUL.paraBanco());
        assertEquals(null, CorKanban.NENHUMA.paraBanco());
    }

    // -------------------------------------------------------------- apoio

    private ColunaKanban criarColuna(String nome) {
        ColunaKanban c = new ColunaKanban();
        c.setNome(nome);
        return servico.salvarColuna(c);
    }

    private CardKanban criarCard(ColunaKanban coluna, String titulo) {
        CardKanban c = new CardKanban();
        c.setColunaId(coluna.getId());
        c.setTitulo(titulo);
        return servico.salvarCard(c);
    }

    private List<String> titulos(List<CardKanban> cards) {
        return cards.stream().map(CardKanban::getTitulo).toList();
    }

    private int contarNoBanco(String sql) throws Exception {
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String textoNoBanco(String sql) throws Exception {
        try (PreparedStatement ps = Database.conexao().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
