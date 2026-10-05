package br.com.myapp.agendas;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.data.Database;
import br.com.myapp.modules.inicio.InicioService;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.modules.lembretes.TipoRecorrencia;
import br.com.myapp.security.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os avisos dos eventos do Google e a agenda por período (semana, mês, ano).
 *
 * Os avisos são testados chamando a varredura com um "agora" escolhido, como
 * o agendador faria a cada 20 s — sem esperar o relógio.
 */
class AvisosEVisoesTest {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    @TempDir
    Path pastaTemporaria;

    private final List<AvisosDeAgenda.AlertaDeAgenda> avisos = new CopyOnWriteArrayList<>();
    private final Consumer<AvisosDeAgenda.AlertaDeAgenda> ouvinte = avisos::add;

    @BeforeEach
    void preparar() {
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        AgendaService.redefinir();
        Database.fechar();
        Database.conexao();
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        EventBus.ouvir(AvisosDeAgenda.AlertaDeAgenda.class, ouvinte);
    }

    @AfterEach
    void limpar() {
        EventBus.deixarDeOuvir(AvisosDeAgenda.AlertaDeAgenda.class, ouvinte);
        AgendaService.redefinir();
        SecurityService.trancar();
        Database.fechar();
        SecurityService.redefinir();
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    // ------------------------------------------------------------- apoio

    private static String utc(LocalDateTime local) {
        return UTC.format(local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC));
    }

    private static String evento(String uid, String titulo, LocalDateTime de, LocalDateTime ate) {
        return "BEGIN:VEVENT\nUID:" + uid + "\nDTSTAMP:20260101T000000Z\nDTSTART:" + utc(de)
                + "\nDTEND:" + utc(ate) + "\nSUMMARY:" + titulo + "\nEND:VEVENT\n";
    }

    private static String diaInteiro(String uid, String titulo, LocalDate de, LocalDate ateExclusivo) {
        return "BEGIN:VEVENT\nUID:" + uid + "\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:" + DIA.format(de)
                + "\nDTEND;VALUE=DATE:" + DIA.format(ateExclusivo) + "\nSUMMARY:" + titulo + "\nEND:VEVENT\n";
    }

    /**
     * Uma agenda conectada há {@code horasAtras} horas, já com o conteúdo.
     * Conectar "no passado" é o que deixa os avisos de antes de agora valerem.
     */
    private Agenda agenda(List<Integer> antecedencias, boolean diaInteiro, long horasAtras, String... eventos) {
        Agenda a = new Agenda();
        a.setNome("Trabalho");
        a.setLink("https://calendar.google.com/x/basic.ics");
        a.setAntecedencias(antecedencias);
        a.setAvisarDiaInteiro(diaInteiro);
        a.setCriadoEm(LocalDateTime.now().minusHours(horasAtras));
        a = AgendaService.salvar(a);
        String ics = "BEGIN:VCALENDAR\nVERSION:2.0\n" + String.join("", eventos) + "END:VCALENDAR\n";
        new AgendaDao().gravarSincronizacao(a.getId(), ics, false, LocalDateTime.now());
        return AgendaService.porId(a.getId()).orElseThrow();
    }

    // ------------------------------------------------------------ avisos

    @Test
    @DisplayName("O aviso sai quando chega a hora, e uma vez só")
    void avisaUmaVez() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(10), false, 2, evento("r1", "Reunião", agora.plusMinutes(8), agora.plusMinutes(68)));

        AvisosDeAgenda.varrer(agora);
        AvisosDeAgenda.varrer(agora.plusSeconds(20));

        assertEquals(1, avisos.size());
        assertEquals("Reunião", avisos.get(0).evento().evento().titulo());
        assertEquals("Em 10 minutos", avisos.get(0).resumoTempo());
        assertFalse(avisos.get(0).atrasado());
    }

    @Test
    @DisplayName("Antes da hora do aviso, nada")
    void antesDaHora() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(10), false, 2, evento("r1", "Reunião", agora.plusMinutes(30), agora.plusMinutes(60)));

        AvisosDeAgenda.varrer(agora);

        assertTrue(avisos.isEmpty());
    }

    @Test
    @DisplayName("Evento que chegou do Google depois da hora do aviso ainda avisa")
    void chegouAtrasadoDoGoogle() {
        // A reunião começa em 3 min, com aviso de 10: a hora do aviso já
        // passou antes de o MyApp saber dela. Tem de sair agora.
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(10), false, 2, evento("r1", "Reunião em cima da hora",
                agora.plusMinutes(3), agora.plusMinutes(33)));

        AvisosDeAgenda.varrer(agora);

        assertEquals(1, avisos.size());
    }

    @Test
    @DisplayName("Evento que já terminou não avisa")
    void jaTerminou() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(0), false, 5, evento("r1", "Reunião da manhã",
                agora.minusHours(2), agora.minusHours(1)));

        AvisosDeAgenda.varrer(agora);

        assertTrue(avisos.isEmpty());
    }

    @Test
    @DisplayName("Conectar a agenda não despeja os avisos de antes da conexão")
    void naoAvisaAntesDeConectar() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        // Conectada agora; a reunião começou há 5 min e vai até daqui a 55.
        agenda(List.of(0), false, 0, evento("r1", "Em andamento", agora.minusMinutes(5), agora.plusMinutes(55)));

        AvisosDeAgenda.varrer(agora.plusMinutes(1));

        assertTrue(avisos.isEmpty());
    }

    @Test
    @DisplayName("Agenda sem antecedência marcada não avisa")
    void semAviso() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(), false, 2, evento("r1", "Reunião", agora.plusMinutes(1), agora.plusMinutes(30)));

        AvisosDeAgenda.varrer(agora.plusMinutes(1));

        assertTrue(avisos.isEmpty());
    }

    @Test
    @DisplayName("Dia inteiro só avisa se a agenda pedir, às 9h")
    void diaInteiro() {
        LocalDate hoje = LocalDate.now();
        String aniversario = diaInteiro("a1", "Aniversário da Ana", hoje, hoje.plusDays(1));
        LocalDateTime nove = hoje.atTime(AvisosDeAgenda.HORA_PADRAO_DIA_INTEIRO);

        agenda(List.of(10), false, 30, aniversario);
        AvisosDeAgenda.varrer(nove.plusMinutes(1));
        assertTrue(avisos.isEmpty(), "sem a opção, aniversário não avisa");

        AgendaService.listar().forEach(a -> AgendaService.excluir(a.getId()));
        agenda(List.of(10), true, 30, aniversario);
        AvisosDeAgenda.varrer(nove.minusMinutes(1));
        assertTrue(avisos.isEmpty(), "antes das 9h, ainda não");
        AvisosDeAgenda.varrer(nove.plusMinutes(1));
        assertEquals(1, avisos.size());
        assertEquals("Hoje", avisos.get(0).resumoTempo());
    }

    @Test
    @DisplayName("Dia inteiro avisa na hora que a agenda escolheu")
    void diaInteiroNaHoraEscolhida() {
        LocalDate hoje = LocalDate.now();
        Agenda a = agenda(List.of(), true, 30, diaInteiro("a1", "Feriado", hoje, hoje.plusDays(1)));
        a.setLink("https://calendar.google.com/x/basic.ics");
        a.setHoraDiaInteiro(java.time.LocalTime.of(7, 30));
        AgendaService.salvar(a);
        assertEquals(java.time.LocalTime.of(7, 30), AgendaService.porId(a.getId()).orElseThrow().getHoraDiaInteiro(),
                "a hora vai e volta do banco");

        AvisosDeAgenda.varrer(hoje.atTime(7, 29));
        assertTrue(avisos.isEmpty(), "às 7h29, ainda não");
        AvisosDeAgenda.varrer(hoje.atTime(7, 30));
        assertEquals(1, avisos.size(), "às 7h30, sim — e não às 9h");
        assertEquals(hoje.atTime(7, 30), avisos.get(0).instanteDoAviso());
    }

    @Test
    @DisplayName("Adiar faz o aviso voltar depois; confirmar não deixa voltar")
    void adiarEConfirmar() {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        agenda(List.of(10), false, 2, evento("r1", "Reunião", agora.plusMinutes(8), agora.plusMinutes(68)));
        AvisosDeAgenda.varrer(agora);
        assertEquals(1, avisos.size());

        AvisosDeAgenda.adiar(avisos.get(0), 5);
        AvisosDeAgenda.varrer(LocalDateTime.now().plusMinutes(2));
        assertEquals(1, avisos.size(), "adiado por 5 min, ainda não volta");

        AvisosDeAgenda.varrer(LocalDateTime.now().plusMinutes(6));
        assertEquals(2, avisos.size(), "passados os 5 min, volta");

        AvisosDeAgenda.confirmar(avisos.get(1));
        AvisosDeAgenda.varrer(LocalDateTime.now().plusMinutes(7));
        assertEquals(2, avisos.size());
    }

    @Test
    @DisplayName("As antecedências vão e voltam do banco; vazio é \"não avisar\"")
    void antecedenciasNoBanco() {
        Agenda a = agenda(List.of(30, 5), true, 0);
        assertEquals(List.of(5, 30), a.getAntecedencias());
        assertTrue(a.isAvisarDiaInteiro());

        a.setLink("https://calendar.google.com/x/basic.ics");
        a.setAntecedencias(List.of());
        a.setAvisarDiaInteiro(false);
        AgendaService.salvar(a);
        Agenda lida = AgendaService.porId(a.getId()).orElseThrow();
        assertTrue(lida.getAntecedencias().isEmpty());
        assertFalse(lida.avisa());
    }

    // ----------------------------------------------------------- visões

    @Test
    @DisplayName("Na semana, lembrete \"a cada X min\" aparece uma vez por dia")
    void intervaloUmaVezPorDia() {
        LocalDate segunda = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        Lembrete agua = new Lembrete();
        agua.setTitulo("Beber água");
        agua.setTipo(TipoRecorrencia.INTERVALO);
        agua.setIntervaloMinutos(30);
        agua.setInicio(segunda.minusDays(7).atTime(8, 0));
        agua.setAntecedencias(List.of(0));
        new LembreteService().salvar(agua);

        Map<LocalDate, List<InicioService.CompromissoDoDia>> semana =
                new InicioService().porDia(segunda, segunda.plusDays(6));

        assertEquals(7, semana.size());
        semana.values().forEach(dia -> assertEquals(1, dia.size()));
        assertEquals(segunda.atTime(0, 0), semana.get(segunda).get(0).quando(),
                "a primeira do dia, e não uma por meia hora");
    }

    @Test
    @DisplayName("Férias de vários dias aparecem em cada dia, antes dos com horário")
    void diaInteiroEmCadaDia() {
        LocalDate segunda = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
        agenda(List.of(), false, 0,
                diaInteiro("f1", "Férias", segunda, segunda.plusDays(3)),
                evento("r1", "Reunião", segunda.atTime(9, 0), segunda.atTime(10, 0)));

        Map<LocalDate, List<InicioService.CompromissoDoDia>> semana =
                new InicioService().porDia(segunda, segunda.plusDays(6));

        assertEquals("Férias", semana.get(segunda).get(0).titulo(), "dia inteiro vem primeiro");
        assertEquals("Reunião", semana.get(segunda).get(1).titulo());
        assertTrue(semana.get(segunda.plusDays(1)).get(0).diaInteiro());
        assertTrue(semana.get(segunda.plusDays(2)).get(0).diaInteiro());
        assertFalse(semana.containsKey(segunda.plusDays(3)), "o DTEND do dia inteiro é exclusivo");
    }

    @Test
    @DisplayName("O ano inteiro sai, mesmo fora da janela de 40 dias do cache")
    void anoInteiro() {
        int ano = LocalDate.now().getYear() + 1;
        agenda(List.of(), false, 0,
                evento("j", "Janeiro", LocalDate.of(ano, 1, 15).atTime(9, 0), LocalDate.of(ano, 1, 15).atTime(10, 0)),
                evento("d", "Dezembro", LocalDate.of(ano, 12, 15).atTime(9, 0), LocalDate.of(ano, 12, 15).atTime(10, 0)));

        Map<LocalDate, List<InicioService.CompromissoDoDia>> doAno =
                new InicioService().porDia(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31));

        assertTrue(doAno.containsKey(LocalDate.of(ano, 1, 15)));
        assertTrue(doAno.containsKey(LocalDate.of(ano, 12, 15)));
    }
}
