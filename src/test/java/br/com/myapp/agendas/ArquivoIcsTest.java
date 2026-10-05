package br.com.myapp.agendas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A leitura do .ics, com o formato que o Google realmente manda.
 *
 * Os casos são os que quebram agenda na prática: série com exceção, uma
 * ocorrência remarcada, uma cancelada, convite recusado, dia inteiro e fuso.
 * Os horários são escritos no fuso de São Paulo e conferidos já convertidos
 * para o relógio da máquina que roda o teste.
 */
class ArquivoIcsTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static final String CABECALHO = """
            BEGIN:VCALENDAR
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            VERSION:2.0
            CALSCALE:GREGORIAN
            METHOD:PUBLISH
            X-WR-CALNAME:lucas@exemplo.com.br
            X-WR-TIMEZONE:America/Sao_Paulo
            BEGIN:VTIMEZONE
            TZID:America/Sao_Paulo
            X-LIC-LOCATION:America/Sao_Paulo
            BEGIN:STANDARD
            TZOFFSETFROM:-0300
            TZOFFSETTO:-0300
            TZNAME:-03
            DTSTART:19700101T000000
            END:STANDARD
            END:VTIMEZONE
            """;

    /** Daily de segunda a sexta às 9h, sem a de quarta (7/10), e a de terça movida para as 10h. */
    private static final String DAILY = """
            BEGIN:VEVENT
            DTSTART;TZID=America/Sao_Paulo:20261001T090000
            DTEND;TZID=America/Sao_Paulo:20261001T091500
            RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
            EXDATE;TZID=America/Sao_Paulo:20261007T090000
            DTSTAMP:20261005T120000Z
            UID:daily@google.com
            SUMMARY:Daily do time
            DESCRIPTION:Pauta rápida\\, sem enrolar.\\n\\n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~
             :~:~:~:~:~:~:~:~:~:~:~::~:~::-\\nParticipe com o Google Meet: https://meet.g
             oogle.com/abc-defg-hij\\n-::~:~::~:~:~:~:~:~:~::-
            STATUS:CONFIRMED
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/Sao_Paulo:20261006T100000
            DTEND;TZID=America/Sao_Paulo:20261006T101500
            DTSTAMP:20261005T120000Z
            UID:daily@google.com
            RECURRENCE-ID;TZID=America/Sao_Paulo:20261006T090000
            SUMMARY:Daily do time (remarcada)
            STATUS:CONFIRMED
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/Sao_Paulo:20261008T090000
            DTEND;TZID=America/Sao_Paulo:20261008T091500
            DTSTAMP:20261005T120000Z
            UID:daily@google.com
            RECURRENCE-ID;TZID=America/Sao_Paulo:20261008T090000
            SUMMARY:Daily do time
            STATUS:CANCELLED
            END:VEVENT
            """;

    private static final String AVULSOS = """
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261005
            DTEND;VALUE=DATE:20261006
            DTSTAMP:20261005T120000Z
            UID:aniver@google.com
            SUMMARY:Aniversário da Ana
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261005T180000Z
            DTEND:20261005T190000Z
            DTSTAMP:20261005T120000Z
            UID:zimmer@google.com
            SUMMARY:Reunião Zimmermann
            LOCATION:Sala 2
            ATTENDEE;CN=Lucas;PARTSTAT=DECLINED;RSVP=TRUE:mailto:lucas@exemplo.com.br
            ATTENDEE;CN=Cliente;PARTSTAT=ACCEPTED:mailto:cliente@zimmermann.com.br
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261005T200000Z
            DTEND:20261005T210000Z
            DTSTAMP:20261005T120000Z
            UID:cancelada@google.com
            SUMMARY:Reunião desmarcada
            STATUS:CANCELLED
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/Sao_Paulo:20261005T130000
            DTEND;TZID=America/Sao_Paulo:20261005T150000
            DTSTAMP:20261005T120000Z
            UID:longa@google.com
            SUMMARY:
            LOCATION:https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc
            END:VEVENT
            """;

    private static final String FIM = "END:VCALENDAR\n";

    private static ArquivoIcs arquivo() {
        return new ArquivoIcs(CABECALHO + DAILY + AVULSOS + FIM);
    }

    private static LocalDateTime sp(int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(2026, mes, dia, hora, minuto, 0, 0, SP)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static List<Evento> daSemana(boolean ocultarRecusados) {
        return arquivo().eventosEntre(LocalDate.of(2026, 10, 5).atStartOfDay(),
                LocalDate.of(2026, 10, 10).atStartOfDay(), ocultarRecusados);
    }

    private static List<Evento> comTitulo(List<Evento> eventos, String prefixo) {
        return eventos.stream().filter(e -> e.titulo().startsWith(prefixo)).toList();
    }

    // ---------------------------------------------------------- recorrência

    @Test
    @DisplayName("Série semanal respeita a exceção, a remarcada e a cancelada")
    void serieComExcecoes() {
        List<Evento> daily = comTitulo(daSemana(true), "Daily");

        // seg 5 às 9h, ter 6 remarcada para as 10h, qua 7 excluída (EXDATE),
        // qui 8 cancelada sozinha, sex 9 às 9h.
        assertEquals(List.of(sp(10, 5, 9, 0), sp(10, 6, 10, 0), sp(10, 9, 9, 0)),
                daily.stream().map(Evento::inicio).toList());
        assertEquals("Daily do time (remarcada)", daily.get(1).titulo());
        assertEquals(sp(10, 5, 9, 15), daily.get(0).fim());
    }

    @Test
    @DisplayName("A ocorrência remarcada não aparece duas vezes")
    void remarcadaNaoDuplica() {
        long naTerca = comTitulo(daSemana(true), "Daily").stream()
                .filter(e -> e.inicio().toLocalDate().equals(LocalDate.of(2026, 10, 6)))
                .count();
        assertEquals(1, naTerca);
    }

    // --------------------------------------------------------- filtragem

    @Test
    @DisplayName("Evento cancelado não aparece")
    void canceladoSome() {
        assertTrue(comTitulo(daSemana(false), "Reunião desmarcada").isEmpty());
    }

    @Test
    @DisplayName("Convite recusado some só quando a agenda pede")
    void recusado() {
        assertTrue(comTitulo(daSemana(true), "Reunião Zimmermann").isEmpty());
        assertEquals(1, comTitulo(daSemana(false), "Reunião Zimmermann").size());
    }

    // ------------------------------------------------------------- campos

    @Test
    @DisplayName("Dia inteiro vai de meia-noite a meia-noite")
    void diaInteiro() {
        Evento aniversario = comTitulo(daSemana(true), "Aniversário").get(0);
        assertTrue(aniversario.diaInteiro());
        assertEquals(LocalDate.of(2026, 10, 5).atStartOfDay(), aniversario.inicio());
        assertEquals(LocalDate.of(2026, 10, 6).atStartOfDay(), aniversario.fim());
    }

    @Test
    @DisplayName("Horário em UTC chega no relógio da máquina")
    void fusoUtc() {
        Evento zimmer = comTitulo(daSemana(false), "Reunião Zimmermann").get(0);
        LocalDateTime esperado = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(esperado, zimmer.inicio());
        assertEquals("Sala 2", zimmer.local());
        assertFalse(zimmer.diaInteiro());
    }

    @Test
    @DisplayName("Link do Meet sai da descrição, e o bloco de instruções some dela")
    void linkEDescricao() {
        Evento daily = comTitulo(daSemana(true), "Daily do time").get(0);
        assertEquals("https://meet.google.com/abc-defg-hij", daily.linkReuniao());
        assertEquals("Pauta rápida, sem enrolar.", daily.descricao());
    }

    @Test
    @DisplayName("Link do Teams no local também vale; sem título vira \"(sem título)\"")
    void teamsESemTitulo() {
        Evento longa = comTitulo(daSemana(true), "(sem título)").get(0);
        assertTrue(longa.linkReuniao().startsWith("https://teams.microsoft.com/"));
        assertNull(comTitulo(daSemana(true), "Aniversário").get(0).linkReuniao());
    }

    @Test
    @DisplayName("Consulta no meio de uma reunião ainda a encontra")
    void emAndamento() {
        LocalDateTime meio = sp(10, 5, 14, 0);
        List<Evento> agora = arquivo().eventosEntre(meio, meio.plusMinutes(1), true);
        Evento longa = comTitulo(agora, "(sem título)").get(0);
        assertTrue(longa.emAndamento(meio));
        assertFalse(longa.jaTerminou(meio));
    }

    @Test
    @DisplayName("O que terminou exatamente no início da consulta fica de fora")
    void terminouNoInicio() {
        List<Evento> depois = arquivo().eventosEntre(sp(10, 5, 15, 0), sp(10, 5, 16, 0), true);
        assertTrue(comTitulo(depois, "(sem título)").isEmpty());
    }

    // ------------------------------------------------------------- defeitos

    @Test
    @DisplayName("Página que não é agenda é recusada com explicação")
    void naoEhAgenda() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ArquivoIcs("<html>Faça login</html>"));
        assertTrue(e.getMessage().contains("Endereço secreto"));
    }

    @Test
    @DisplayName("Descrição em HTML vira texto")
    void descricaoHtml() {
        assertEquals("Linha 1\nLinha 2 & fim",
                ArquivoIcs.limparDescricao("<b>Linha 1</b><br>Linha 2 &amp; fim"));
        assertEquals("", ArquivoIcs.limparDescricao(null));
    }
}
