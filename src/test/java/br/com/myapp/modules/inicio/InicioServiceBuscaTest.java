package br.com.myapp.modules.inicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A regra de "o que casa" da busca na agenda.
 *
 * O cálculo das ocorrências e a leitura das agendas já têm os seus testes; aqui
 * fica só o que a busca decide: acento, maiúscula e quantas palavras.
 */
class InicioServiceBuscaTest {

    @Test
    @DisplayName("ignora acento e maiúscula")
    void ignoraAcentoEMaiuscula() {
        List<String> palavras = InicioService.palavrasDaBusca("FÉRIAS");
        assertTrue(InicioService.casa(palavras, "ferias do Lucas"));
        assertTrue(InicioService.casa(InicioService.palavrasDaBusca("ferias"), "Férias"));
    }

    @Test
    @DisplayName("com mais de uma palavra, exige todas, em qualquer ordem")
    void exigeTodasAsPalavras() {
        List<String> palavras = InicioService.palavrasDaBusca("folga maio");
        assertTrue(InicioService.casa(palavras, "Folga do Lucas", "compensação de maio"));
        assertTrue(InicioService.casa(InicioService.palavrasDaBusca("maio folga"), "Folga em maio"));
        assertFalse(InicioService.casa(palavras, "Folga em junho"));
    }

    @Test
    @DisplayName("procura em qualquer dos textos: título, descrição e local")
    void procuraEmTodosOsTextos() {
        List<String> palavras = InicioService.palavrasDaBusca("zimmermann");
        assertTrue(InicioService.casa(palavras, "Reunião", "Validar integração", "Sala Zimmermann"));
        assertTrue(InicioService.casa(palavras, "Reunião", "Cliente Zimmermann", ""));
        assertFalse(InicioService.casa(palavras, "Reunião", "Validar integração", ""));
    }

    @Test
    @DisplayName("texto nulo não derruba a busca")
    void textoNuloNaoDerruba() {
        assertFalse(InicioService.casa(InicioService.palavrasDaBusca("folga"), "Reunião", null, null));
        assertTrue(InicioService.casa(InicioService.palavrasDaBusca("folga"), null, "Folga"));
    }

    @Test
    @DisplayName("termo vazio ou só com espaços não tem palavras")
    void termoVazio() {
        assertTrue(InicioService.palavrasDaBusca("").isEmpty());
        assertTrue(InicioService.palavrasDaBusca("   ").isEmpty());
        assertTrue(InicioService.palavrasDaBusca(null).isEmpty());
    }

    @Test
    @DisplayName("espaços repetidos entre as palavras não criam palavras vazias")
    void espacosRepetidos() {
        assertEquals(List.of("folga", "maio"), InicioService.palavrasDaBusca("  folga    maio "));
    }
}
