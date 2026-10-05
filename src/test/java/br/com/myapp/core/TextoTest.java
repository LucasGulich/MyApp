package br.com.myapp.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

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
    @DisplayName("Hora digitada do jeito que se fala")
    void hora() {
        LocalTime padrao = LocalTime.of(9, 0);
        assertEquals(LocalTime.of(8, 0), Texto.interpretarHora("8", padrao));
        assertEquals(LocalTime.of(8, 0), Texto.interpretarHora("8h", padrao));
        assertEquals(LocalTime.of(8, 30), Texto.interpretarHora("8h30", padrao));
        assertEquals(LocalTime.of(8, 30), Texto.interpretarHora("8.30", padrao));
        assertEquals(LocalTime.of(8, 30), Texto.interpretarHora("0830", padrao));
        assertEquals(LocalTime.of(18, 45), Texto.interpretarHora(" 18:45 ", padrao));
        assertEquals(LocalTime.of(1, 0), Texto.interpretarHora("25", padrao), "dá a volta no relógio");
        assertEquals(padrao, Texto.interpretarHora("amanhã", padrao));
        assertEquals(padrao, Texto.interpretarHora("", padrao));
        assertEquals(padrao, Texto.interpretarHora(null, padrao));
    }

    @Test
    @DisplayName("Termo vazio casa com tudo; texto nulo só com termo vazio")
    void termoVazio() {
        assertTrue(Texto.contem("qualquer coisa", "   "));
        assertTrue(Texto.contem(null, ""));
        assertFalse(Texto.contem(null, "agua"));
    }
}
