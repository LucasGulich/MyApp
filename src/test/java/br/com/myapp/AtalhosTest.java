package br.com.myapp;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.data.Database;
import br.com.myapp.modules.atalhos.Atalho;
import br.com.myapp.modules.atalhos.AtalhoService;
import br.com.myapp.modules.atalhos.GrupoAtalho;
import br.com.myapp.modules.atalhos.TipoAtalho;
import br.com.myapp.security.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Atalhos: grupos, cadastro, ordem, exclusão lógica e o que se deduz do caminho. */
class AtalhosTest {

    @TempDir
    Path pastaTemporaria;

    private AtalhoService servico;

    @BeforeEach
    void preparar() {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        Database.fechar();
        Database.conexao();
        servico = new AtalhoService();
    }

    @AfterEach
    void limpar() {
        Database.fechar();
        SecurityService.redefinir();
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    // ------------------------------------------------------------- grupos

    @Test
    @DisplayName("Os três grupos do pedido nascem na primeira abertura, e só nela")
    void gruposIniciais() {
        servico.criarGruposIniciaisNaPrimeiraVez();
        List<GrupoAtalho> grupos = servico.grupos();
        assertEquals(List.of("Aplicativos do dia a dia", "Sites do dia a dia", "Pastas na rede"),
                grupos.stream().map(GrupoAtalho::getNome).toList());

        // Excluir todos e reabrir não traz os exemplos de volta.
        grupos.forEach(g -> servico.excluirGrupo(g.getId()));
        servico.criarGruposIniciaisNaPrimeiraVez();
        assertTrue(servico.grupos().isEmpty());
    }

    @Test
    @DisplayName("Grupo sem nome não salva")
    void grupoSemNome() {
        GrupoAtalho g = new GrupoAtalho();
        g.setNome("   ");
        assertThrows(IllegalArgumentException.class, () -> servico.salvarGrupo(g));
    }

    // ------------------------------------------------------------ atalhos

    @Test
    @DisplayName("Atalho salva com ícone, e nome em branco vira o sugerido pelo caminho")
    void salvarAtalho() {
        GrupoAtalho g = grupo("Apps");
        Atalho a = new Atalho();
        a.setGrupoId(g.getId());
        a.setTipo(TipoAtalho.APLICATIVO);
        a.setAlvo("  C:\\Program Files\\DBeaver\\dbeaver.exe  ");
        a.setIconePng(new byte[]{1, 2, 3});
        servico.salvar(a);

        Atalho lido = servico.grupos().get(0).getAtalhos().get(0);
        assertEquals("dbeaver", lido.getNome());
        assertEquals("C:\\Program Files\\DBeaver\\dbeaver.exe", lido.getAlvo());
        assertArrayEquals(new byte[]{1, 2, 3}, lido.getIconePng());
        assertEquals(Atalho.ModoIcone.AUTOMATICO, lido.getModoIcone());
    }

    @Test
    @DisplayName("Atalho sem alvo não salva")
    void semAlvo() {
        Atalho a = new Atalho();
        a.setGrupoId(grupo("Apps").getId());
        assertThrows(IllegalArgumentException.class, () -> servico.salvar(a));
    }

    @Test
    @DisplayName("Reordenar, mover de grupo e excluir — sem DELETE no banco")
    void ordemMoverExcluir() throws Exception {
        GrupoAtalho apps = grupo("Apps");
        GrupoAtalho sites = grupo("Sites");
        Atalho a = atalho(apps, "A");
        Atalho b = atalho(apps, "B");
        Atalho c = atalho(apps, "C");

        servico.reordenar(List.of(c, a, b));
        assertEquals(List.of("C", "A", "B"), nomes(servico.grupos().get(0)));

        servico.moverParaGrupo(a, sites.getId());
        assertEquals(List.of("C", "B"), nomes(servico.grupos().get(0)));
        assertEquals(List.of("A"), nomes(servico.grupos().get(1)));

        servico.excluir(b.getId());
        assertEquals(List.of("C"), nomes(servico.grupos().get(0)));
        // Continua no banco, só marcado.
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT data_exclusao FROM atalho WHERE id = ?")) {
            ps.setLong(1, b.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getLong(1) > 0);
            }
        }
    }

    @Test
    @DisplayName("Abrir o que não existe explica o que houve, sem tentar abrir nada")
    void abrirInexistente() {
        Atalho local = new Atalho();
        local.setTipo(TipoAtalho.APLICATIVO);
        local.setAlvo(pastaTemporaria.resolve("sumiu.exe").toString());
        IOException e = assertThrows(IOException.class, () -> servico.abrir(local));
        assertTrue(e.getMessage().contains("movido, renomeado ou desinstalado"));

        Atalho rede = new Atalho();
        rede.setTipo(TipoAtalho.PASTA);
        rede.setAlvo("\\\\servidor-que-nao-existe-xyz\\pasta");
        assertTrue(assertThrows(IOException.class, () -> servico.abrir(rede))
                .getMessage().contains("rede"));
    }

    @Test
    @DisplayName("Site guarda o navegador escolhido; desinstalado, abrir explica o que fazer")
    void navegadorDoSite() {
        GrupoAtalho g = grupo("Sites");
        Atalho a = new Atalho();
        a.setGrupoId(g.getId());
        a.setNome("Portal");
        a.setTipo(TipoAtalho.SITE);
        a.setAlvo("portal.empresa.com.br");
        a.setNavegador(pastaTemporaria.resolve("navegador-que-sumiu.exe").toString());
        servico.salvar(a);

        Atalho lido = servico.grupos().get(0).getAtalhos().get(0);
        assertEquals(a.getNavegador(), lido.getNavegador());
        IOException e = assertThrows(IOException.class, () -> servico.abrir(lido));
        assertTrue(e.getMessage().contains("não está mais instalado"));
        assertTrue(e.getMessage().contains("escolha outro"));

        // Sem escolha, fica vazio — o padrão do Windows.
        lido.setNavegador(null);
        servico.salvar(lido);
        assertEquals("", servico.grupos().get(0).getAtalhos().get(0).getNavegador());
    }

    // ------------------------------------------------- deduzir do caminho

    @Test
    @DisplayName("O tipo sai do que foi colado ou arrastado")
    void deduzirTipo() throws Exception {
        Path pasta = Files.createDirectory(pastaTemporaria.resolve("Projetos"));
        assertEquals(TipoAtalho.SITE, AtalhoService.deduzirTipo("https://github.com"));
        assertEquals(TipoAtalho.SITE, AtalhoService.deduzirTipo("www.google.com"));
        assertEquals(TipoAtalho.APLICATIVO, AtalhoService.deduzirTipo("C:\\x\\Teams.lnk"));
        assertEquals(TipoAtalho.APLICATIVO, AtalhoService.deduzirTipo("C:\\x\\programa.EXE"));
        assertEquals(TipoAtalho.PASTA, AtalhoService.deduzirTipo(pasta.toString()));
        assertEquals(TipoAtalho.PASTA, AtalhoService.deduzirTipo("\\\\servidor\\compartilhado\\Clientes"));
        assertEquals(TipoAtalho.ARQUIVO, AtalhoService.deduzirTipo("\\\\servidor\\docs\\planilha.xlsx"));
        assertEquals(TipoAtalho.ARQUIVO, AtalhoService.deduzirTipo("C:\\x\\relatorio.pdf"));
    }

    @Test
    @DisplayName("O nome sugerido: sem extensão de programa, só o domínio do site")
    void nomeSugerido() {
        assertEquals("Teams", AtalhoService.nomeSugerido("C:\\x\\Teams.lnk", TipoAtalho.APLICATIVO));
        assertEquals("dbeaver", AtalhoService.nomeSugerido("C:\\x\\dbeaver.exe", TipoAtalho.APLICATIVO));
        assertEquals("relatorio.pdf", AtalhoService.nomeSugerido("C:\\x\\relatorio.pdf", TipoAtalho.ARQUIVO));
        assertEquals("Clientes", AtalhoService.nomeSugerido("\\\\srv\\dados\\Clientes\\", TipoAtalho.PASTA));
        assertEquals("github.com", AtalhoService.nomeSugerido("https://www.github.com/LucasGulich", TipoAtalho.SITE));
        assertEquals("portal.empresa.com.br", AtalhoService.nomeSugerido("portal.empresa.com.br/login", TipoAtalho.SITE));
    }

    @Test
    @DisplayName("Argumentos separados como no Windows, respeitando aspas")
    void argumentos() {
        assertEquals(List.of("--profile", "Minha Empresa", "-x"),
                AtalhoService.partirArgumentos("--profile \"Minha Empresa\"  -x"));
        assertEquals(List.of(), AtalhoService.partirArgumentos("   "));
        assertEquals(List.of(""), AtalhoService.partirArgumentos("\"\""));
    }

    @Test
    @DisplayName("Site sem https ganha o https")
    void normalizarSite() {
        assertEquals("https://github.com", AtalhoService.normalizarSite("github.com"));
        assertEquals("http://intranet", AtalhoService.normalizarSite("http://intranet"));
    }

    // -------------------------------------------------------------- apoio

    private GrupoAtalho grupo(String nome) {
        GrupoAtalho g = new GrupoAtalho();
        g.setNome(nome);
        return servico.salvarGrupo(g);
    }

    private Atalho atalho(GrupoAtalho g, String nome) {
        Atalho a = new Atalho();
        a.setGrupoId(g.getId());
        a.setNome(nome);
        a.setTipo(TipoAtalho.SITE);
        a.setAlvo("https://" + nome.toLowerCase() + ".com");
        return servico.salvar(a);
    }

    private static List<String> nomes(GrupoAtalho g) {
        return g.getAtalhos().stream().map(Atalho::getNome).toList();
    }
}
