package br.com.myapp.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextoTest {

    @Test
    @DisplayName("Tira acentos, cedilha e maiúsculas")
    void normaliza() {
        assertEquals("agua", Texto.paraBusca("Água"));
        assertEquals("acao", Texto.paraBusca("AÇÃO"));
        assertEquals("pao de acucar", Texto.paraBusca("Pão de Açúcar"));
        assertEquals("", Texto.paraBusca(null));
    }

    @Test
    @DisplayName("A busca acha com ou sem acento, dos dois lados")
    void buscaIgnoraAcento() {
        assertTrue(Texto.contem("Conta de água", "agua"));
        assertTrue(Texto.contem("Conta de agua", "água"));
        assertTrue(Texto.contem("Configuração do servidor", "CONFIGURACAO"));
        assertFalse(Texto.contem("Conta de luz", "agua"));
    }

    @Test
    @DisplayName("Termo vazio casa com tudo; texto nulo só com termo vazio")
    void termoVazio() {
        assertTrue(Texto.contem("qualquer coisa", "   "));
        assertTrue(Texto.contem(null, ""));
        assertFalse(Texto.contem(null, "agua"));
    }
}
