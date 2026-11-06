package br.com.myapp.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Quem decide o navegador: atalho → preferência do MyApp → Windows. */
class LinksTest {

    private static final String CHROME = "C:\\Chrome\\chrome.exe";
    private static final String EDGE = "C:\\Edge\\msedge.exe";
    private static final Predicate<String> TUDO_EXISTE = exe -> true;
    private static final Predicate<String> NADA_EXISTE = exe -> false;

    @Test
    @DisplayName("O navegador do atalho vale mais que a preferência geral")
    void atalhoVenceGeral() {
        assertEquals(Optional.of(EDGE), Links.escolher(EDGE, CHROME, TUDO_EXISTE));
    }

    @Test
    @DisplayName("Sem escolha no atalho, vale a preferência do MyApp")
    void geralQuandoAtalhoVazio() {
        assertEquals(Optional.of(CHROME), Links.escolher("", CHROME, TUDO_EXISTE));
        assertEquals(Optional.of(CHROME), Links.escolher(null, CHROME, TUDO_EXISTE));
    }

    @Test
    @DisplayName("Sem nenhuma escolha, o Windows decide")
    void windowsSemEscolha() {
        assertEquals(Optional.empty(), Links.escolher("", "", TUDO_EXISTE));
        assertEquals(Optional.empty(), Links.escolher(null, null, TUDO_EXISTE));
    }

    @Test
    @DisplayName("Preferência geral desinstalada cai no Windows, sem travar o link")
    void geralSumidaCaiNoWindows() {
        assertEquals(Optional.empty(), Links.escolher("", CHROME, NADA_EXISTE));
    }

    @Test
    @DisplayName("O do atalho volta mesmo sumido: quem abre avisa, em vez de trocar calado")
    void atalhoSumidoNaoETrocado() {
        assertEquals(Optional.of(EDGE), Links.escolher(EDGE, CHROME, NADA_EXISTE));
    }

    @Test
    @DisplayName("Acha a sala no meio do texto, sem a pontuação grudada no fim")
    void linkDeReuniaoNoTexto() {
        assertEquals(Optional.of("https://meet.google.com/abc-defg-hij"),
                Links.linkDeReuniao("Pauta (https://meet.google.com/abc-defg-hij)."));
        assertEquals(Optional.of("https://empresa.zoom.us/j/123"),
                Links.linkDeReuniao("Entrar: https://empresa.zoom.us/j/123"));
        assertEquals(Optional.empty(), Links.linkDeReuniao("https://sysmo.com.br/chamado/1"));
        assertEquals(Optional.empty(), Links.linkDeReuniao(null));
    }

    @Test
    @DisplayName("Sala de reunião vira \"Entrar\"; outro link continua \"Abrir\"")
    void ehReuniao() {
        assertEquals(true, Links.ehReuniao("https://teams.microsoft.com/l/meetup-join/x"));
        assertEquals(false, Links.ehReuniao("https://sysmo.com.br"));
        assertEquals(false, Links.ehReuniao("C:\\Pasta"));
    }
}
