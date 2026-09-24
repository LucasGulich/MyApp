package br.com.myapp.modules.lembretes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do cálculo de ocorrências.
 *
 * Esta e a regra que decide se um aviso sai ou não sai, entao vale conferir
 * caso a caso - inclusive as bordas que costumam passar despercebidas, como
 * dia 31 em fevereiro e 29 de fevereiro em ano comum.
 */
class CalculadoraOcorrenciasTest {

    private Lembrete base(TipoRecorrencia tipo, LocalDateTime inicio) {
        Lembrete l = new Lembrete();
        l.setId(1L);
        l.setTitulo("teste");
        l.setTipo(tipo);
        l.setInicio(inicio);
        return l;
    }

    // ------------------------------------------------------------------ Único

    @Test
    @DisplayName("Lembrete único aparece uma vez dentro da janela")
    void unicoDentroDaJanela() {
        LocalDateTime quando = LocalDateTime.of(2026, 9, 17, 14, 0);
        Lembrete l = base(TipoRecorrencia.UNICO, quando);

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 0, 0),
                LocalDateTime.of(2026, 9, 18, 0, 0));

        assertEquals(1, r.size());
        assertEquals(quando, r.get(0));
    }

    @Test
    @DisplayName("Lembrete único fora da janela não aparece")
    void unicoForaDaJanela() {
        Lembrete l = base(TipoRecorrencia.UNICO, LocalDateTime.of(2026, 9, 17, 14, 0));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 18, 0, 0),
                LocalDateTime.of(2026, 9, 19, 0, 0));

        assertTrue(r.isEmpty());
    }

    // ----------------------------------------------------------------- diario

    @Test
    @DisplayName("Diario gera uma ocorrência por dia, sempre no mesmo horário")
    void diario() {
        Lembrete l = base(TipoRecorrencia.DIARIO, LocalDateTime.of(2026, 9, 17, 9, 30));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 0, 0),
                LocalDateTime.of(2026, 9, 20, 23, 59));

        assertEquals(4, r.size());
        assertTrue(r.stream().allMatch(d -> d.getHour() == 9 && d.getMinute() == 30));
    }

    @Test
    @DisplayName("Diario não gera nada antes da data de inicio")
    void diarioRespeitaInicio() {
        Lembrete l = base(TipoRecorrencia.DIARIO, LocalDateTime.of(2026, 9, 20, 9, 0));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 0, 0),
                LocalDateTime.of(2026, 9, 22, 23, 59));

        assertEquals(3, r.size());
        assertEquals(LocalDateTime.of(2026, 9, 20, 9, 0), r.get(0));
    }

    @Test
    @DisplayName("Data de termino encerra a repetição")
    void respeitaTermino() {
        Lembrete l = base(TipoRecorrencia.DIARIO, LocalDateTime.of(2026, 9, 17, 9, 0));
        l.setFim(LocalDateTime.of(2026, 9, 19, 23, 59));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 0, 0),
                LocalDateTime.of(2026, 9, 30, 0, 0));

        assertEquals(3, r.size());
    }

    // ------------------------------------------------------------- dias úteis

    @Test
    @DisplayName("Dias úteis pula sábado e domingo")
    void diasUteis() {
        // 14/09/2026 e uma segunda-feira.
        Lembrete l = base(TipoRecorrencia.DIAS_UTEIS, LocalDateTime.of(2026, 9, 14, 8, 0));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 14, 0, 0),
                LocalDateTime.of(2026, 9, 20, 23, 59));

        assertEquals(5, r.size());
        assertFalse(r.stream().anyMatch(d ->
                d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY));
    }

    // ---------------------------------------------------------------- semanal

    @Test
    @DisplayName("Semanal respeita apenas os dias escolhidos")
    void semanal() {
        Lembrete l = base(TipoRecorrencia.SEMANAL, LocalDateTime.of(2026, 9, 14, 10, 0));
        l.setDiasSemana(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 14, 0, 0),
                LocalDateTime.of(2026, 9, 27, 23, 59));

        assertEquals(4, r.size());   // 2 semanas x 2 dias
        assertTrue(r.stream().allMatch(d ->
                d.getDayOfWeek() == DayOfWeek.MONDAY || d.getDayOfWeek() == DayOfWeek.WEDNESDAY));
    }

    // ----------------------------------------------------------------- mensal

    @Test
    @DisplayName("Mensal repete no mesmo dia todo mês")
    void mensal() {
        Lembrete l = base(TipoRecorrencia.MENSAL, LocalDateTime.of(2026, 1, 10, 15, 0));
        l.setDiaMes(10);

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 3, 31, 23, 59));

        assertEquals(3, r.size());
        assertTrue(r.stream().allMatch(d -> d.getDayOfMonth() == 10));
    }

    @Test
    @DisplayName("Dia 31 em mês curto cai no último dia, em vez de sumir")
    void mensalDia31EmFevereiro() {
        Lembrete l = base(TipoRecorrencia.MENSAL, LocalDateTime.of(2026, 1, 31, 12, 0));
        l.setDiaMes(31);

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59));

        assertEquals(1, r.size());
        assertEquals(28, r.get(0).getDayOfMonth());   // 2026 não e bissexto
    }

    // ------------------------------------------------------------------ anual

    @Test
    @DisplayName("Anual repete na mesma data todo ano")
    void anual() {
        Lembrete l = base(TipoRecorrencia.ANUAL, LocalDateTime.of(2026, 3, 15, 8, 0));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2027, 12, 31, 23, 59));

        assertEquals(2, r.size());
        assertTrue(r.stream().allMatch(d -> d.getMonthValue() == 3 && d.getDayOfMonth() == 15));
    }

    @Test
    @DisplayName("29 de fevereiro cai no dia 28 em ano comum")
    void anualEm29DeFevereiro() {
        Lembrete l = base(TipoRecorrencia.ANUAL, LocalDateTime.of(2024, 2, 29, 10, 0));

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0));

        assertEquals(1, r.size());
        assertEquals(28, r.get(0).getDayOfMonth());
    }

    // -------------------------------------------------------------- intervalo

    @Test
    @DisplayName("Intervalo repete a cada X minutos")
    void intervalo() {
        Lembrete l = base(TipoRecorrencia.INTERVALO, LocalDateTime.of(2026, 9, 17, 8, 0));
        l.setIntervaloMinutos(30);

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 8, 0),
                LocalDateTime.of(2026, 9, 17, 10, 0));

        assertEquals(5, r.size());   // 8:00, 8:30, 9:00, 9:30, 10:00
    }

    @Test
    @DisplayName("Intervalo entra na janela alinhado ao inicio, sem varrer desde o começo")
    void intervaloAlinhaNaJanela() {
        Lembrete l = base(TipoRecorrencia.INTERVALO, LocalDateTime.of(2026, 9, 1, 0, 0));
        l.setIntervaloMinutos(45);

        List<LocalDateTime> r = CalculadoraOcorrencias.entre(l,
                LocalDateTime.of(2026, 9, 17, 10, 0),
                LocalDateTime.of(2026, 9, 17, 11, 0));

        assertFalse(r.isEmpty());
        // Todas as ocorrências são múltiplos de 45 min contados desde o inicio.
        assertTrue(r.stream().allMatch(d ->
                java.time.Duration.between(l.getInicio(), d).toMinutes() % 45 == 0));
    }

    // ----------------------------------------------------------------- Próxima

    @Test
    @DisplayName("Próxima ocorrência encontra o evento anual mesmo distante")
    void proximaEncontraAnual() {
        Lembrete l = base(TipoRecorrencia.ANUAL, LocalDateTime.of(2026, 12, 25, 9, 0));

        var proxima = CalculadoraOcorrencias.proxima(l, LocalDateTime.of(2026, 9, 17, 12, 0));

        assertTrue(proxima.isPresent());
        assertEquals(LocalDateTime.of(2026, 12, 25, 9, 0), proxima.get());
    }

    @Test
    @DisplayName("Lembrete único que já passou não tem próxima ocorrência")
    void proximaVaziaQuandoJaPassou() {
        Lembrete l = base(TipoRecorrencia.UNICO, LocalDateTime.of(2026, 1, 1, 9, 0));

        var proxima = CalculadoraOcorrencias.proxima(l, LocalDateTime.of(2026, 9, 17, 12, 0));

        assertTrue(proxima.isEmpty());
    }

    // ------------------------------------------------------------- Validação

    @Test
    @DisplayName("Semanal sem dia escolhido e recusado na validação")
    void validaSemanalSemDias() {
        Lembrete l = base(TipoRecorrencia.SEMANAL, LocalDateTime.now());
        assertEquals("Escolha pelo menos um dia da semana.", l.validar());
    }

    @Test
    @DisplayName("Lembrete sem título e recusado na validação")
    void validaTituloVazio() {
        Lembrete l = base(TipoRecorrencia.UNICO, LocalDateTime.now());
        l.setTitulo("   ");
        assertEquals("Escreva um título para o lembrete.", l.validar());
    }

    @Test
    @DisplayName("Antecedências voltam ordenadas e sem repetição")
    void antecedenciasNormalizadas() {
        Lembrete l = new Lembrete();
        l.antecedenciasCsv("60, 5, 5, 0");
        assertEquals(List.of(0, 5, 60), l.getAntecedencias());
        assertEquals("0,5,60", l.antecedenciasCsv());
    }
}
