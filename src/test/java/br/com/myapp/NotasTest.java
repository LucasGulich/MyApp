package br.com.myapp;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.data.Database;
import br.com.myapp.modules.notas.Categoria;
import br.com.myapp.modules.notas.CampoNota;
import br.com.myapp.modules.notas.DestacadorSintaxe;
import br.com.myapp.modules.notas.Nota;
import br.com.myapp.modules.notas.NotaDao;
import br.com.myapp.modules.notas.NotaService;
import br.com.myapp.modules.notas.TipoNota;
import br.com.myapp.security.GeradorSenha;
import br.com.myapp.security.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do módulo de notas.
 *
 * O foco está nas garantias que o módulo promete: o segredo nunca vai em
 * texto claro para o banco, a busca não vaza senha, o tipo molda os campos e
 * nada é apagado de verdade.
 */
class NotasTest {

    @TempDir
    Path pastaTemporaria;

    private NotaService servico;

    @BeforeEach
    void preparar() {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        Database.fechar();
        Database.conexao();

        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        servico = new NotaService();
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

    // ----------------------------------------------------------- segurança

    @Test
    @DisplayName("A senha de uma credencial nunca vai em texto claro para o banco")
    void senhaSempreCifrada() throws Exception {
        String segredo = "SenhaDoServidor#2026";

        Nota nota = new Nota();
        nota.setTitulo("Servidor de producao");
        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Usuário", "admin", false);
        nota.definirCampo("Senha", segredo, true);
        // De propósito: a nota NAO esta marcada como protegida.
        nota.setProtegida(false);
        servico.salvar(nota);

        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT chave, valor FROM nota_campo WHERE nota_id = ? AND data_exclusao IS NULL")) {
            ps.setLong(1, nota.getId());
            try (ResultSet rs = ps.executeQuery()) {
                boolean achouSenha = false;
                while (rs.next()) {
                    String chave = rs.getString("chave");
                    String valor = rs.getString("valor");
                    if ("Senha".equals(chave)) {
                        achouSenha = true;
                        assertFalse(valor.contains(segredo), "a senha vazou em texto claro");
                        assertTrue(valor.startsWith("enc:v1:"), "a senha deveria estar cifrada");
                    }
                    if ("Usuário".equals(chave)) {
                        // Campo comum de nota nao protegida fica legivel, por escolha.
                        assertEquals("admin", valor);
                    }
                }
                assertTrue(achouSenha, "o campo Senha nao foi gravado");
            }
        }
    }

    @Test
    @DisplayName("Nota protegida cifra título, corpo e campos")
    void notaProtegidaCifraTudo() throws Exception {
        Nota nota = new Nota();
        nota.setTitulo("Assunto reservado");
        nota.setCorpo("conteudo confidencial");
        nota.setTipo(TipoNota.LIVRE);
        nota.setProtegida(true);
        servico.salvar(nota);

        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT titulo, conteudo FROM nota WHERE id = ?")) {
            ps.setLong(1, nota.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString("titulo").startsWith("enc:v1:"));
                assertTrue(rs.getString("conteudo").startsWith("enc:v1:"));
            }
        }

        // Destrancado, volta legivel.
        Nota lida = servico.porId(nota.getId()).orElseThrow();
        assertEquals("Assunto reservado", lida.getTitulo());
        assertEquals("conteudo confidencial", lida.getCorpo());
    }

    @Test
    @DisplayName("Com o app trancado, a nota protegida não se revela")
    void notaProtegidaComAppTrancado() {
        Nota nota = new Nota();
        nota.setTitulo("Assunto reservado");
        nota.setTipo(TipoNota.LIVRE);
        nota.setProtegida(true);
        servico.salvar(nota);

        SecurityService.trancar();

        Nota lida = servico.porId(nota.getId()).orElseThrow();
        assertEquals(NotaDao.PROTEGIDO, lida.getTitulo());
    }

    @Test
    @DisplayName("Com o app trancado, o segredo não se revela nem em nota comum")
    void segredoComAppTrancado() {
        Nota nota = new Nota();
        nota.setTitulo("Acesso do cliente");
        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Usuário", "operador", false);
        nota.definirCampo("Senha", "segredo-forte", true);
        servico.salvar(nota);

        SecurityService.trancar();

        Nota lida = servico.porId(nota.getId()).orElseThrow();
        assertEquals("Acesso do cliente", lida.getTitulo(), "nota comum continua legivel");
        assertEquals("operador", lida.valor("Usuário").orElse(""));
        assertEquals(NotaDao.SEGREDO_OCULTO, lida.valor("Senha").orElse(""),
                "o segredo nao pode aparecer com o app trancado");
    }

    @Test
    @DisplayName("A busca não encontra notas pelo valor da senha")
    void buscaNaoVazaSegredo() {
        Nota nota = new Nota();
        nota.setTitulo("Acesso ao portal");
        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Usuário", "admin", false);
        nota.definirCampo("Senha", "batatafrita77", true);
        servico.salvar(nota);

        List<Nota> porTitulo = servico.buscar("portal", servico.listarTodas());
        assertEquals(1, porTitulo.size(), "deveria achar pelo titulo");

        List<Nota> porSenha = servico.buscar("batatafrita", servico.listarTodas());
        assertTrue(porSenha.isEmpty(), "procurar pela senha nao pode revelar a nota");

        List<Nota> porUsuario = servico.buscar("admin", servico.listarTodas());
        assertEquals(1, porUsuario.size(), "campo comum entra na busca normalmente");
    }

    @Test
    @DisplayName("A busca ignora acentos: \"agua\" acha \"Água\"")
    void buscaIgnoraAcentos() {
        Nota nota = new Nota();
        nota.setTitulo("Conta de Água");
        nota.setTipo(TipoNota.LIVRE);
        nota.ajustarCamposAoTipo();
        nota.setCorpo("Vence no dia 10, pagar pelo aplicativo do banco.");
        servico.salvar(nota);

        assertEquals(1, servico.buscar("agua", servico.listarTodas()).size());
        assertEquals(1, servico.buscar("AGUA", servico.listarTodas()).size());
        assertEquals(1, servico.buscar("aplicatívo", servico.listarTodas()).size(),
                "acento no termo também não pode atrapalhar");
    }

    @Test
    @DisplayName("Fixada fica no topo também em \"Minha ordem\"")
    void fixadaNoTopoNaOrdemManual() {
        Nota primeira = servico.salvar(notaLivre("Primeira"));
        Nota segunda = servico.salvar(notaLivre("Segunda"));
        Nota terceira = servico.salvar(notaLivre("Terceira"));
        servico.reordenar(List.of(primeira, segunda, terceira));

        servico.alternarFixada(terceira);

        List<String> titulos = servico.naMinhaOrdem(servico.listarTodas()).stream()
                .map(Nota::getTitulo).toList();
        assertEquals(List.of("Terceira", "Primeira", "Segunda"), titulos,
                "a fixada sobe, e as outras mantêm a ordem arrastada");
    }

    private static Nota notaLivre(String titulo) {
        Nota nota = new Nota();
        nota.setTitulo(titulo);
        nota.setTipo(TipoNota.LIVRE);
        nota.ajustarCamposAoTipo();
        return nota;
    }

    // ---------------------------------------------------------- tipos

    @Test
    @DisplayName("O tipo define os campos do formulário")
    void tipoDefineCampos() {
        Nota credencial = new Nota();
        credencial.setTipo(TipoNota.CREDENCIAL);
        credencial.ajustarCamposAoTipo();
        assertEquals(3, credencial.getCampos().size());
        assertTrue(credencial.getCampos().stream().anyMatch(CampoNota::isSensivel));

        Nota livre = new Nota();
        livre.setTipo(TipoNota.LIVRE);
        livre.ajustarCamposAoTipo();
        assertTrue(livre.getCampos().isEmpty());

        assertEquals("Senha", TipoNota.CREDENCIAL.campoDeSegredo());
        assertFalse(TipoNota.LINK.temSegredo());
    }

    @Test
    @DisplayName("Trocar o tipo preserva o que já estava preenchido")
    void trocaDeTipoPreservaDados() {
        Nota nota = new Nota();
        nota.setTitulo("Em transformacao");
        nota.setTipo(TipoNota.LIVRE);
        nota.setCorpo("anotacao original");
        nota.definirCampo("Observação", "algo meu", false);

        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();

        assertEquals("anotacao original", nota.getCorpo());
        assertEquals("algo meu", nota.valor("Observação").orElse(""));
        assertTrue(nota.valor("Usuário").isEmpty(), "o campo novo nasce vazio");
        assertEquals(1, nota.camposExtras().size(), "o campo proprio continua sendo extra");
    }

    @Test
    @DisplayName("Link sem endereço é recusado")
    void validacaoDoLink() {
        Nota nota = new Nota();
        nota.setTitulo("Portal");
        nota.setTipo(TipoNota.LINK);
        nota.ajustarCamposAoTipo();

        assertEquals("Informe o endereço do link.", nota.validar());

        nota.definirCampo("URL", "exemplo.com.br", false);
        assertEquals(null, nota.validar());
    }

    @Test
    @DisplayName("Nota sem título é recusada")
    void validacaoDoTitulo() {
        Nota nota = new Nota();
        nota.setTitulo("   ");
        assertEquals("Escreva um título para a nota.", nota.validar());
        assertThrows(IllegalArgumentException.class, () -> servico.salvar(nota));
    }

    // ------------------------------------------------------ persistência

    @Test
    @DisplayName("A nota volta do banco inteira")
    void persistenciaCompleta() {
        Nota nota = new Nota();
        nota.setTitulo("Consulta de pedidos");
        nota.setTipo(TipoNota.CODIGO);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Linguagem", "SQL", false);
        nota.setCorpo("SELECT * FROM pedido WHERE data >= '2026-01-01'");
        nota.setFavorita(true);
        servico.salvar(nota);

        Nota lida = servico.porId(nota.getId()).orElseThrow();
        assertEquals("Consulta de pedidos", lida.getTitulo());
        assertEquals(TipoNota.CODIGO, lida.getTipo());
        assertEquals("SQL", lida.valor("Linguagem").orElse(""));
        assertTrue(lida.getCorpo().startsWith("SELECT"));
        assertTrue(lida.isFavorita());
    }

    @Test
    @DisplayName("Excluir a nota não apaga a linha do banco")
    void exclusaoLogica() throws Exception {
        Nota nota = new Nota();
        nota.setTitulo("Descartavel");
        servico.salvar(nota);

        servico.excluir(nota.getId());

        assertTrue(servico.listarTodas().isEmpty(), "sai da lista");
        assertEquals(1, servico.listarExcluidas().size(), "aparece na lixeira");

        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT data_exclusao FROM nota WHERE id = ?")) {
            ps.setLong(1, nota.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "a linha sumiu do banco");
                rs.getLong(1);
                assertFalse(rs.wasNull(), "data_exclusao deveria estar preenchida");
            }
        }

        servico.restaurar(nota.getId());
        assertEquals(1, servico.listarTodas().size(), "voltou para a lista");
    }

    @Test
    @DisplayName("Guardar a senha anterior ao trocá-la")
    void historicoDeSenha() {
        Nota nota = new Nota();
        nota.setTitulo("Acesso rotativo");
        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Senha", "primeira-senha", true);
        servico.salvar(nota);

        Nota lida = servico.porId(nota.getId()).orElseThrow();
        lida.definirCampo("Senha", "segunda-senha", true);
        servico.salvar(lida);

        List<NotaDao.ValorAnterior> historico =
                servico.historicoDoCampo(nota.getId(), "Senha");

        assertEquals(1, historico.size());
        assertEquals("primeira-senha", historico.get(0).valor());
    }

    // ----------------------------------------------------------- categorias

    @Test
    @DisplayName("Excluir a categoria não leva as notas junto")
    void exclusaoDeCategoriaPreservaNotas() {
        Categoria categoria = new Categoria();
        categoria.setNome("Temporaria");
        servico.salvarCategoria(categoria);

        Nota nota = new Nota();
        nota.setTitulo("Nota da categoria");
        nota.setCategoriaId(categoria.getId());
        servico.salvar(nota);

        servico.excluirCategoria(categoria.getId());

        Nota lida = servico.porId(nota.getId()).orElseThrow();
        assertFalse(lida.isExcluida(), "a nota nao pode ser excluida junto");
        assertEquals(null, lida.getCategoriaId(), "deveria ter ficado sem categoria");
        assertEquals(1, servico.listarSemCategoria().size());
    }

    @Test
    @DisplayName("Categoria com nome repetido é recusada")
    void categoriaDuplicada() {
        Categoria primeira = new Categoria();
        primeira.setNome("Clientes");
        servico.salvarCategoria(primeira);

        Categoria segunda = new Categoria();
        segunda.setNome("clientes");   // mesmo nome, outra caixa

        assertThrows(IllegalArgumentException.class, () -> servico.salvarCategoria(segunda));
    }

    @Test
    @DisplayName("As categorias iniciais são criadas uma única vez")
    void categoriasIniciais() {
        servico.criarCategoriasIniciaisSeVazio();
        int depoisDaPrimeira = servico.listarCategorias().size();
        assertTrue(depoisDaPrimeira > 0, "deveria ter criado as categorias iniciais");

        servico.criarCategoriasIniciaisSeVazio();
        assertEquals(depoisDaPrimeira, servico.listarCategorias().size(),
                "a segunda chamada nao pode duplicar");
    }

    // ------------------------------------------------------ gerador de senha

    @Test
    @DisplayName("O gerador respeita o tamanho pedido")
    void geradorRespeitaTamanho() {
        GeradorSenha.Opcoes opcoes = new GeradorSenha.Opcoes();
        opcoes.tamanho = 24;
        assertEquals(24, GeradorSenha.gerar(opcoes).length());
    }

    @Test
    @DisplayName("O gerador inclui ao menos um caractere de cada grupo marcado")
    void geradorIncluiTodosOsGrupos() {
        GeradorSenha.Opcoes opcoes = new GeradorSenha.Opcoes();
        opcoes.tamanho = 12;

        // Repete porque o resultado é aleatório: uma execução só não prova nada.
        for (int i = 0; i < 40; i++) {
            String senha = GeradorSenha.gerar(opcoes);
            assertTrue(senha.chars().anyMatch(Character::isLowerCase), "faltou minuscula");
            assertTrue(senha.chars().anyMatch(Character::isUpperCase), "faltou maiuscula");
            assertTrue(senha.chars().anyMatch(Character::isDigit), "faltou digito");
            assertTrue(senha.chars().anyMatch(c -> !Character.isLetterOrDigit(c)), "faltou simbolo");
        }
    }

    @Test
    @DisplayName("Evitar ambíguos remove os caracteres confusos")
    void geradorEvitaAmbiguos() {
        GeradorSenha.Opcoes opcoes = new GeradorSenha.Opcoes();
        opcoes.tamanho = 48;
        opcoes.evitarAmbiguos = true;
        opcoes.simbolos = false;

        for (int i = 0; i < 20; i++) {
            String senha = GeradorSenha.gerar(opcoes);
            assertFalse(senha.contains("l"), "l e facil de confundir com 1");
            assertFalse(senha.contains("I"), "I e facil de confundir com l");
            assertFalse(senha.contains("O"), "O e facil de confundir com 0");
            assertFalse(senha.contains("0"));
            assertFalse(senha.contains("1"));
        }
    }

    @Test
    @DisplayName("Duas senhas geradas não saem iguais")
    void geradorNaoRepete() {
        assertNotEquals(GeradorSenha.gerar(), GeradorSenha.gerar());
    }

    @Test
    @DisplayName("A frase-senha tem a quantidade de palavras pedida")
    void fraseSenha() {
        String frase = GeradorSenha.gerarFrase(4, "-");
        // 4 palavras mais o número no fim.
        assertEquals(5, frase.split("-").length);
    }

    // ------------------------------------------------------ destaque de código

    @Test
    @DisplayName("O destaque separa palavras reservadas do resto")
    void destaqueDeSql() {
        var fluxo = DestacadorSintaxe.destacar(
                "SELECT nome FROM cliente WHERE id = 10 -- comentario", "SQL");

        assertFalse(fluxo.getChildren().isEmpty());

        boolean temPalavra = fluxo.getChildren().stream()
                .anyMatch(n -> n.getStyleClass().contains("cod-palavra"));
        boolean temNumero = fluxo.getChildren().stream()
                .anyMatch(n -> n.getStyleClass().contains("cod-numero"));
        boolean temComentario = fluxo.getChildren().stream()
                .anyMatch(n -> n.getStyleClass().contains("cod-comentario"));

        assertTrue(temPalavra, "SELECT deveria ter sido reconhecido");
        assertTrue(temNumero, "10 deveria ter sido reconhecido");
        assertTrue(temComentario, "o comentario deveria ter sido reconhecido");
    }

    @Test
    @DisplayName("Texto puro não recebe destaque, e nada se perde")
    void destaqueDeTextoPuro() {
        String original = "apenas um texto qualquer";
        var fluxo = DestacadorSintaxe.destacar(original, "Texto");

        StringBuilder junto = new StringBuilder();
        fluxo.getChildren().forEach(n -> junto.append(((javafx.scene.text.Text) n).getText()));

        assertEquals(original, junto.toString());
    }

    @Test
    @DisplayName("O destaque preserva o código inteiro, sem perder caracteres")
    void destaqueNaoPerdeConteudo() {
        String codigo = """
                public class Teste {
                    // comentario
                    private int numero = 42;
                    private String texto = "ola";
                }""";

        var fluxo = DestacadorSintaxe.destacar(codigo, "Java");

        StringBuilder junto = new StringBuilder();
        fluxo.getChildren().forEach(n -> junto.append(((javafx.scene.text.Text) n).getText()));

        assertEquals(codigo, junto.toString(), "o destaque nao pode alterar o codigo");
    }

    // ----------------------------------------------------------------- resumo

    @Test
    @DisplayName("O resumo na lista nunca mostra a senha")
    void resumoNaoMostraSegredo() {
        Nota nota = new Nota();
        nota.setTitulo("Acesso");
        nota.setTipo(TipoNota.CREDENCIAL);
        nota.ajustarCamposAoTipo();
        nota.definirCampo("Usuário", "operador", false);
        nota.definirCampo("Senha", "nao-deve-aparecer", true);

        assertFalse(nota.resumo().contains("nao-deve-aparecer"));
        assertTrue(nota.resumo().contains("operador"));
    }

    @Test
    @DisplayName("Duplicar copia os campos")
    void duplicacao() {
        Nota original = new Nota();
        original.setTitulo("Modelo");
        original.setTipo(TipoNota.CREDENCIAL);
        original.ajustarCamposAoTipo();
        original.definirCampo("Usuário", "raiz", false);
        original.definirCampo("Senha", "abc123", true);
        servico.salvar(original);

        Nota copia = servico.duplicar(original);

        assertTrue(copia.getTitulo().contains("cópia"));
        assertEquals("raiz", copia.valor("Usuário").orElse(""));
        assertEquals("abc123", copia.valor("Senha").orElse(""));
        assertNotEquals(original.getId(), copia.getId());
    }
}
