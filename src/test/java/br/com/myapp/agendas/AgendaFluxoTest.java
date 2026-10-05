package br.com.myapp.agendas;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.data.Database;
import br.com.myapp.modules.inicio.InicioService;
import br.com.myapp.security.SecurityService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A agenda do Google de ponta a ponta: cadastrar, baixar, guardar, mostrar
 * na tela Hoje, falhar sem perder a cópia, proteger e excluir.
 *
 * O "Google" aqui é um servidor HTTP de mentira, na própria máquina, que
 * devolve o .ics que cada teste mandar — inclusive erro.
 */
class AgendaFluxoTest {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    @TempDir
    Path pastaTemporaria;

    private HttpServer google;
    private volatile int codigoResposta = 200;
    private volatile String resposta = "";

    @BeforeEach
    void preparar() throws IOException {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        AgendaService.redefinir();
        Database.fechar();
        Database.conexao();
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        google = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        google.createContext("/", troca -> {
            byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(codigoResposta, corpo.length == 0 ? -1 : corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        google.start();
    }

    @AfterEach
    void limpar() {
        google.stop(0);
        AgendaService.redefinir();
        SecurityService.trancar();
        Database.fechar();
        SecurityService.redefinir();
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    // ------------------------------------------------------------- apoio

    private String link() {
        return "http://127.0.0.1:" + google.getAddress().getPort() + "/calendar/ical/x/private-abc/basic.ics";
    }

    private static String utc(LocalDateTime local) {
        return UTC.format(local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC));
    }

    /** Um evento amanhã às 10h e o aniversário de hoje, de dia inteiro. */
    private static String agendaPadrao() {
        LocalDateTime amanha10h = LocalDate.now().plusDays(1).atTime(10, 0);
        LocalDate hoje = LocalDate.now();
        return "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Google Inc//Google Calendar//EN\n"
                + "BEGIN:VEVENT\nUID:reuniao@google.com\nDTSTAMP:20260101T000000Z\n"
                + "DTSTART:" + utc(amanha10h) + "\nDTEND:" + utc(amanha10h.plusHours(1)) + "\n"
                + "SUMMARY:Reunião com o cliente\n"
                + "DESCRIPTION:Entrar: https://meet.google.com/abc-defg-hij\nEND:VEVENT\n"
                + "BEGIN:VEVENT\nUID:aniver@google.com\nDTSTAMP:20260101T000000Z\n"
                + "DTSTART;VALUE=DATE:" + DIA.format(hoje) + "\n"
                + "DTEND;VALUE=DATE:" + DIA.format(hoje.plusDays(1)) + "\n"
                + "SUMMARY:Aniversário da Ana\nEND:VEVENT\n"
                + "END:VCALENDAR\n";
    }

    private Agenda conectar(String nome, boolean protegida) {
        Agenda a = new Agenda();
        a.setNome(nome);
        a.setLink(link());
        a.setProtegida(protegida);
        return AgendaService.salvar(a);
    }

    private static String colunaCrua(long id, String coluna) throws Exception {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT " + coluna + " FROM agenda WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static List<String> titulosDosProximos() {
        return new InicioService().proximosDias(3).stream()
                .map(InicioService.CompromissoDoDia::titulo).toList();
    }

    // ---------------------------------------------------------- cadastro

    @Test
    @DisplayName("O endereço secreto vai cifrado para o banco")
    void linkCifrado() throws Exception {
        Agenda a = conectar("Trabalho", false);

        String guardado = colunaCrua(a.getId(), "link");
        assertTrue(guardado.startsWith("enc:v1:"), "o link nunca pode ir em claro");
        assertFalse(guardado.contains("private-abc"));
        assertEquals(link(), AgendaService.link(a.getId()).orElseThrow());
    }

    @Test
    @DisplayName("Conectar agenda exige o aplicativo destrancado")
    void exigeDestrancado() {
        SecurityService.trancar();
        Agenda a = new Agenda();
        a.setNome("Trabalho");
        a.setLink(link());
        assertThrows(IllegalStateException.class, () -> AgendaService.salvar(a));
    }

    @Test
    @DisplayName("Endereço sem https é recusado com explicação")
    void enderecoInvalido() {
        Agenda a = new Agenda();
        a.setNome("Trabalho");
        a.setLink("http://calendar.google.com/agenda.ics");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AgendaService.salvar(a));
        assertTrue(e.getMessage().contains("https://"));
    }

    @Test
    @DisplayName("webcal:// vira https:// ao salvar")
    void webcal() {
        assertEquals("https://calendar.google.com/x.ics",
                AgendaService.normalizar("  webcal://calendar.google.com/x.ics "));
    }

    // ------------------------------------------------------- sincronização

    @Test
    @DisplayName("Sincronizar põe os eventos na tela Hoje")
    void sincronizaEMostra() throws Exception {
        resposta = agendaPadrao();
        Agenda a = conectar("Trabalho", false);

        assertTrue(AgendaService.sincronizar(a.getId()));

        assertTrue(titulosDosProximos().contains("Reunião com o cliente"));
        List<AgendaService.EventoNaAgenda> diaInteiro = new InicioService().diaInteiroDeHoje();
        assertEquals(1, diaInteiro.size());
        assertEquals("Aniversário da Ana", diaInteiro.get(0).evento().titulo());

        Agenda lida = AgendaService.porId(a.getId()).orElseThrow();
        assertNotNull(lida.getSincronizadaEm());
        assertNull(lida.getUltimoErro());
        assertTrue(colunaCrua(a.getId(), "conteudo").contains("Reunião com o cliente"),
                "agenda comum guarda a cópia em claro, como um lembrete comum");
    }

    @Test
    @DisplayName("Falha de rede registra o erro e mantém a última cópia")
    void falhaMantemCopia() {
        resposta = agendaPadrao();
        Agenda a = conectar("Trabalho", false);
        assertTrue(AgendaService.sincronizar(a.getId()));

        codigoResposta = 404;
        resposta = "";
        assertFalse(AgendaService.sincronizar(a.getId()));

        Agenda lida = AgendaService.porId(a.getId()).orElseThrow();
        assertTrue(lida.getUltimoErro().contains("não encontrou"));
        assertFalse(lida.getUltimoErro().contains("127.0.0.1"), "a mensagem nunca leva o endereço");
        assertTrue(titulosDosProximos().contains("Reunião com o cliente"),
                "a cópia anterior continua valendo");

        // Voltou: o erro some.
        codigoResposta = 200;
        resposta = agendaPadrao();
        assertTrue(AgendaService.sincronizar(a.getId()));
        assertNull(AgendaService.porId(a.getId()).orElseThrow().getUltimoErro());
    }

    @Test
    @DisplayName("Página que não é agenda vira erro legível, não exceção")
    void respostaQueNaoEhAgenda() {
        resposta = "<html>Faça login no Google</html>";
        Agenda a = conectar("Trabalho", false);
        assertFalse(AgendaService.sincronizar(a.getId()));
        assertTrue(AgendaService.porId(a.getId()).orElseThrow().getUltimoErro()
                .contains("Endereço secreto"));
    }

    @Test
    @DisplayName("Depois de trancar, a sincronização continua (endereço na memória)")
    void sincronizaTrancado() {
        resposta = agendaPadrao();
        Agenda a = conectar("Trabalho", false);
        SecurityService.trancar();

        assertTrue(AgendaService.sincronizar(a.getId()));
        assertTrue(titulosDosProximos().contains("Reunião com o cliente"));
    }

    @Test
    @DisplayName("Sem nunca ter destrancado, não há como sincronizar — e não é erro")
    void semDestrancarNaoSincroniza() {
        resposta = agendaPadrao();
        Agenda a = conectar("Trabalho", false);
        SecurityService.trancar();
        AgendaService.redefinir();   // como se o aplicativo tivesse acabado de abrir

        assertFalse(AgendaService.sincronizar(a.getId()));
        assertNull(AgendaService.porId(a.getId()).orElseThrow().getUltimoErro());
    }

    // ----------------------------------------------------------- proteção

    @Test
    @DisplayName("Agenda protegida guarda a cópia cifrada")
    void protegidaCifrada() throws Exception {
        resposta = agendaPadrao();
        Agenda a = conectar("Pessoal", true);
        assertTrue(AgendaService.sincronizar(a.getId()));

        String guardado = colunaCrua(a.getId(), "conteudo");
        assertTrue(guardado.startsWith("enc:v1:"));
        assertFalse(guardado.contains("Reunião"));
        assertTrue(titulosDosProximos().contains("Reunião com o cliente"));
    }

    @Test
    @DisplayName("Agenda protegida, ao reabrir trancado, não mostra nada")
    void protegidaReabertaTrancada() {
        resposta = agendaPadrao();
        Agenda a = conectar("Pessoal", true);
        assertTrue(AgendaService.sincronizar(a.getId()));

        SecurityService.trancar();
        AgendaService.redefinir();

        assertTrue(AgendaService.eventosEntre(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3)).isEmpty());
    }

    // ----------------------------------------------------------- exclusão

    @Test
    @DisplayName("Excluir é lógico: some da tela, fica no banco")
    void exclusaoLogica() throws Exception {
        resposta = agendaPadrao();
        Agenda a = conectar("Trabalho", false);
        assertTrue(AgendaService.sincronizar(a.getId()));

        AgendaService.excluir(a.getId());

        assertTrue(AgendaService.listar().isEmpty());
        assertFalse(titulosDosProximos().contains("Reunião com o cliente"));
        assertNotNull(colunaCrua(a.getId(), "data_exclusao"));
    }

    // -------------------------------------------------------------- testar

    @Test
    @DisplayName("Testar conta os eventos e acha o próximo, sem gravar nada")
    void testar() {
        resposta = agendaPadrao();
        AgendaService.ResultadoTeste r = AgendaService.testar(link());

        assertEquals(2, r.eventos());
        assertEquals("Reunião com o cliente", r.proximo().orElseThrow().titulo());
        assertTrue(AgendaService.listar().isEmpty());
    }

    @Test
    @DisplayName("Testar um endereço que responde erro devolve a explicação")
    void testarComErro() {
        codigoResposta = 403;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AgendaService.testar(link()));
        assertTrue(e.getMessage().contains("recusou"));
    }
}
