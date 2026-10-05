package br.com.myapp.agendas;

import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.core.Scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Os avisos dos eventos das agendas do Google.
 *
 * Roda na mesma batida de 20 s do agendador dos lembretes, mas com uma
 * pergunta diferente. O lembrete pergunta "o que devia ter avisado desde a
 * última varredura?". Aqui a pergunta é <b>"que aviso já passou da hora e
 * ainda não saiu?"</b> — porque um evento pode chegar do Google depois da hora
 * do próprio aviso: a reunião das 14h marcada às 13h55, com aviso de 10 min,
 * só é conhecida na sincronização das 13h57. Com a pergunta dos lembretes, esse
 * aviso nunca sairia; com esta, sai às 13h57.
 *
 * Três travas evitam o exagero:
 *
 *  - evento que <b>já terminou</b> não avisa — avisar uma reunião que acabou
 *    só atrapalha;
 *  - aviso de antes da <b>agenda ser conectada</b> não sai — conectar às 14h05
 *    não pode despejar os avisos da manhã inteira;
 *  - nada além do período de recuperação das configurações (dias de "avisos
 *    perdidos").
 *
 * Evento de dia inteiro (aniversário, feriado) só avisa se a agenda pedir, e
 * uma vez, na hora do dia que a agenda escolher (9h, se ninguém mexer):
 * avisar à meia-noite, ou X minutos antes dela, não serve a ninguém.
 */
public final class AvisosDeAgenda {

    /** A hora em que o evento de dia inteiro avisa, até a agenda escolher outra. */
    public static final LocalTime HORA_PADRAO_DIA_INTEIRO = LocalTime.of(9, 0);

    /** Acima disso, o aviso é mostrado como atrasado. Mesmo valor dos lembretes. */
    private static final int MINUTOS_PARA_CONSIDERAR_ATRASADO = 5;

    /** Publicado quando o aviso de um evento deve aparecer na tela. */
    public record AlertaDeAgenda(AgendaService.EventoNaAgenda evento, int antecedencia, boolean atrasado) {

        /** "Em 10 minutos", "Agora"; "Hoje" para o de dia inteiro. */
        public String resumoTempo() {
            return evento.evento().diaInteiro() ? "Hoje" : Scheduler.resumoTempo(antecedencia);
        }

        /** O instante em que o aviso devia ter saído. */
        public LocalDateTime instanteDoAviso() {
            return AvisosDeAgenda.instanteDoAviso(evento.agenda(), evento.evento(), antecedencia);
        }
    }

    private static final DisparoAgendaDao DAO = new DisparoAgendaDao();

    private AvisosDeAgenda() {
    }

    // ------------------------------------------------------------ varredura

    /** Dispara o que estiver na hora. Chamado pelo agendador. */
    public static void varrer(LocalDateTime agora) {
        try {
            for (Agenda agenda : AgendaService.listar()) {
                if (agenda.avisa()) {
                    verificar(agenda, agora);
                }
            }
            processarAdiados(agora);
        } catch (Exception e) {
            // Falha aqui não pode levar junto os avisos dos lembretes.
            Log.erro("Falha ao verificar os avisos das agendas", e);
        }
    }

    private static void verificar(Agenda agenda, LocalDateTime agora) {
        int maior = agenda.getAntecedencias().stream().mapToInt(Integer::intValue).max().orElse(0);
        LocalDateTime limite = limiteDeRecuperacao(agora);

        List<Evento> eventos = AgendaService.eventosDaAgenda(agenda, agora, agora.plusMinutes(maior + 1L));
        Set<String> jaSairam = DAO.disparados(agenda.getId(), agora.minusDays(2));

        for (Evento e : eventos) {
            if (e.jaTerminou(agora)) {
                continue;
            }
            List<Integer> antecedencias = e.diaInteiro()
                    ? (agenda.isAvisarDiaInteiro() ? List.of(0) : Collections.emptyList())
                    : agenda.getAntecedencias();

            for (int antecedencia : antecedencias) {
                LocalDateTime instante = instanteDoAviso(agenda, e, antecedencia);
                boolean naHora = !instante.isAfter(agora)
                        && !instante.isBefore(limite)
                        && !instante.isBefore(agenda.getCriadoEm());
                if (!naHora || jaSairam.contains(DisparoAgendaDao.chave(e.uid(), e.inicio(), antecedencia))) {
                    continue;
                }
                if (!DAO.registrar(agenda.getId(), e.uid(), e.inicio(), antecedencia)) {
                    continue;   // outra varredura ganhou a corrida
                }
                boolean atrasado = Duration.between(instante, agora).toMinutes() > MINUTOS_PARA_CONSIDERAR_ATRASADO;
                Log.info("Alerta de agenda disparado: agenda=" + agenda.getId()
                        + " Antecedência=" + antecedencia + "min" + (atrasado ? " (atrasado)" : ""));
                EventBus.publicar(new AlertaDeAgenda(
                        new AgendaService.EventoNaAgenda(agenda, e), antecedencia, atrasado));
            }
        }
    }

    /** Reapresenta os avisos que você mandou adiar. */
    private static void processarAdiados(LocalDateTime agora) {
        for (DisparoAgendaDao.Adiado adiado : DAO.adiadosVencidos(agora)) {
            DAO.limparAdiamento(adiado.id());
            AgendaService.porId(adiado.agendaId())
                    .flatMap(agenda -> AgendaService.eventosDaAgenda(agenda,
                                    adiado.ocorrencia().minusMinutes(1), adiado.ocorrencia().plusMinutes(1))
                            .stream()
                            .filter(e -> e.uid().equals(adiado.uid()) && e.inicio().equals(adiado.ocorrencia()))
                            .findFirst()
                            .map(e -> new AgendaService.EventoNaAgenda(agenda, e)))
                    // Sumiu do Google nesse meio-tempo (cancelado): não volta.
                    .ifPresent(e -> EventBus.publicar(new AlertaDeAgenda(e, adiado.antecedencia(), false)));
        }
    }

    private static LocalDateTime limiteDeRecuperacao(LocalDateTime agora) {
        int dias = Config.get().diasDeCatchUp;
        // "0 dias" nas configurações quer dizer não recuperar nada; ainda
        // assim, uma varredura que atrasou alguns minutos não perde o aviso.
        return dias <= 0 ? agora.minusMinutes(10) : agora.minusDays(dias);
    }

    static LocalDateTime instanteDoAviso(Agenda agenda, Evento e, int antecedencia) {
        return e.diaInteiro()
                ? e.inicio().toLocalDate().atTime(agenda.getHoraDiaInteiro())
                : e.inicio().minusMinutes(antecedencia);
    }

    // ---------------------------------------------------------------- ações

    /** "Confirmar" no aviso. */
    public static void confirmar(AlertaDeAgenda alerta) {
        AgendaService.EventoNaAgenda e = alerta.evento();
        DAO.reconhecer(e.agenda().getId(), e.evento().uid(), e.evento().inicio());
    }

    /** "Adiar" no aviso: volta daqui a tantos minutos. */
    public static void adiar(AlertaDeAgenda alerta, int minutos) {
        AgendaService.EventoNaAgenda e = alerta.evento();
        DAO.adiar(e.agenda().getId(), e.evento().uid(), e.evento().inicio(), alerta.antecedencia(),
                LocalDateTime.now().plusMinutes(minutos));
        Log.info("Aviso de agenda adiado por " + minutos + " min.");
    }
}
