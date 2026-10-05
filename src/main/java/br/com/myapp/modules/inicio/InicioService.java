package br.com.myapp.modules.inicio;

import br.com.myapp.agendas.AgendaService;
import br.com.myapp.agendas.Evento;
import br.com.myapp.core.EventBus;
import br.com.myapp.modules.lembretes.CalculadoraOcorrencias;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteDao;
import br.com.myapp.modules.lembretes.TipoRecorrencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Regras da tela inicial.
 *
 * Duas responsabilidades: montar a agenda do dia a partir dos lembretes e das
 * agendas conectadas (Google), e cuidar dos recados.
 *
 * A agenda é só leitura — a tela inicial não altera lembrete nenhum. Ela
 * responde "o que tenho para hoje?" usando exatamente o mesmo cálculo de
 * ocorrências que o agendador usa para disparar os avisos, de modo que o que
 * aparece aqui é o que vai realmente tocar.
 */
public class InicioService {

    /** Publicado quando um recado é criado, alterado, movido ou excluído. */
    public record RecadosMudaram() {
    }

    private final RecadoDao recadoDao = new RecadoDao();
    private final LembreteDao lembreteDao = new LembreteDao();

    // ------------------------------------------------------- agenda do dia

    /**
     * Um compromisso do dia, já com a hora resolvida.
     *
     * Vem de um lembrete do MyApp ou de um evento de agenda externa (Google):
     * exatamente um dos dois está preenchido. A tela não precisa saber de
     * onde veio para desenhar — título, cor e horário saem daqui.
     */
    public record CompromissoDoDia(Lembrete lembrete, AgendaService.EventoNaAgenda evento,
                                   LocalDateTime quando) {

        public static CompromissoDoDia de(Lembrete lembrete, LocalDateTime quando) {
            return new CompromissoDoDia(lembrete, null, quando);
        }

        public static CompromissoDoDia de(AgendaService.EventoNaAgenda evento) {
            return new CompromissoDoDia(null, evento, evento.evento().inicio());
        }

        public boolean ehDaAgenda() {
            return evento != null;
        }

        /** Término, para evento de agenda; lembrete não tem duração. */
        public LocalDateTime fim() {
            return evento == null ? null : evento.evento().fim();
        }

        public String titulo() {
            return evento == null ? lembrete.getTitulo() : evento.evento().titulo();
        }

        public String descricao() {
            return evento == null ? lembrete.getDescricao() : evento.evento().descricao();
        }

        public String cor() {
            return evento == null ? lembrete.getCor() : evento.agenda().getCor();
        }

        /** Conteúdo que precisa ficar escondido com o aplicativo trancado? */
        public boolean protegido() {
            return evento == null ? lembrete.isSensivel() : evento.agenda().isProtegida();
        }

        /**
         * Já passou? Evento de agenda só passa quando termina: a reunião das
         * 15h às 16h continua "acontecendo" às 15h30.
         */
        public boolean jaPassou() {
            LocalDateTime agora = LocalDateTime.now();
            return evento == null ? quando.isBefore(agora) : evento.evento().jaTerminou(agora);
        }

        /** Evento de agenda sem horário: aniversário, feriado, férias. */
        public boolean diaInteiro() {
            return evento != null && evento.evento().diaInteiro();
        }

        /** Começou e ainda não terminou (só evento de agenda tem duração). */
        public boolean emAndamento() {
            return evento != null && evento.evento().emAndamento(LocalDateTime.now());
        }

        /** Está para acontecer na próxima hora? */
        public boolean eIminente() {
            LocalDateTime agora = LocalDateTime.now();
            return !quando.isBefore(agora) && quando.isBefore(agora.plusHours(1));
        }
    }

    /**
     * Tudo o que acontece hoje, em ordem de horário — lembretes e eventos das
     * agendas conectadas, menos os de dia inteiro, que têm faixa própria.
     *
     * Inclui o que já passou: saber que a reunião das 9h passou é tão útil
     * quanto saber da próxima — some da lista e some da cabeça.
     */
    public List<CompromissoDoDia> agendaDeHoje() {
        return agendaDoDia(LocalDate.now());
    }

    /** O mesmo que {@link #agendaDeHoje()}, para um dia qualquer. */
    public List<CompromissoDoDia> agendaDoDia(LocalDate dia) {
        return ocorrenciasEntre(dia.atStartOfDay(), dia.atTime(23, 59, 59), false);
    }

    /** Aniversários, feriados, férias: o que ocupa o dia de hoje sem horário. */
    public List<AgendaService.EventoNaAgenda> diaInteiroDeHoje() {
        return diaInteiroDoDia(LocalDate.now());
    }

    public List<AgendaService.EventoNaAgenda> diaInteiroDoDia(LocalDate dia) {
        return AgendaService.eventosEntre(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                .filter(e -> e.evento().diaInteiro())
                .toList();
    }

    /**
     * Tudo de um período, separado por dia — o que a semana, o mês e o ano
     * desenham. Dia sem nada não aparece no mapa.
     *
     * Duas diferenças da lista do dia, ambas para a grade não virar ruído:
     *
     *  - lembrete "a cada X minutos" entra <b>uma vez por dia</b>, na primeira
     *    ocorrência. Um "beber água a cada 30 min" ocuparia 48 linhas de cada
     *    dia da semana;
     *  - evento de dia inteiro que dura vários dias (férias) aparece em
     *    <b>cada</b> dia que ocupa, e não só no primeiro.
     *
     * Em cada dia, os de dia inteiro vêm primeiro e o resto por horário.
     */
    public Map<LocalDate, List<CompromissoDoDia>> porDia(LocalDate de, LocalDate ate) {
        Map<LocalDate, List<CompromissoDoDia>> mapa = new TreeMap<>();
        LocalDateTime inicio = de.atStartOfDay();
        LocalDateTime fim = ate.atTime(23, 59, 59);

        for (Lembrete lembrete : lembreteDao.listarAtivos()) {
            if (lembrete.getTipo() == TipoRecorrencia.INTERVALO) {
                for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
                    primeiraDoDia(lembrete, d).ifPresent(q ->
                            mapa.computeIfAbsent(q.toLocalDate(), k -> new ArrayList<>())
                                    .add(CompromissoDoDia.de(lembrete, q)));
                }
                continue;
            }
            for (LocalDateTime quando : CalculadoraOcorrencias.entre(lembrete, inicio, fim)) {
                mapa.computeIfAbsent(quando.toLocalDate(), k -> new ArrayList<>())
                        .add(CompromissoDoDia.de(lembrete, quando));
            }
        }

        for (AgendaService.EventoNaAgenda e : AgendaService.eventosEntre(inicio, fim)) {
            Evento ev = e.evento();
            if (ev.diaInteiro()) {
                LocalDate primeiro = ev.inicio().toLocalDate().isBefore(de) ? de : ev.inicio().toLocalDate();
                LocalDate ultimo = ev.fim().toLocalDate().minusDays(1);
                if (ultimo.isAfter(ate)) {
                    ultimo = ate;
                }
                for (LocalDate d = primeiro; !d.isAfter(ultimo); d = d.plusDays(1)) {
                    mapa.computeIfAbsent(d, k -> new ArrayList<>())
                            .add(new CompromissoDoDia(null, e, d.atStartOfDay()));
                }
            } else if (!ev.inicio().isBefore(inicio)) {
                mapa.computeIfAbsent(ev.inicio().toLocalDate(), k -> new ArrayList<>())
                        .add(CompromissoDoDia.de(e));
            }
        }

        Comparator<CompromissoDoDia> ordem = Comparator
                .comparing((CompromissoDoDia c) -> !c.diaInteiro())
                .thenComparing(CompromissoDoDia::quando)
                .thenComparing(CompromissoDoDia::titulo);
        mapa.values().forEach(lista -> lista.sort(ordem));
        return mapa;
    }

    /**
     * A primeira vez que um lembrete "a cada X minutos" acontece num dia.
     *
     * Pede ao cálculo só o pedaço do dia em que ela pode cair — do começo do
     * dia (ou do início do lembrete) até um intervalo depois —, em vez de
     * gerar as 1.440 ocorrências de um "a cada minuto" para jogar fora 1.439.
     */
    private static Optional<LocalDateTime> primeiraDoDia(Lembrete lembrete, LocalDate dia) {
        LocalDateTime desde = dia.atStartOfDay();
        if (lembrete.getInicio().isAfter(desde)) {
            desde = lembrete.getInicio();
        }
        if (!desde.toLocalDate().equals(dia)) {
            return Optional.empty();
        }
        int passo = lembrete.getIntervaloMinutos() == null ? 1 : Math.max(1, lembrete.getIntervaloMinutos());
        LocalDateTime ate = desde.plusMinutes(passo);
        if (ate.toLocalDate().isAfter(dia)) {
            ate = dia.atTime(23, 59, 59);
        }
        return CalculadoraOcorrencias.entre(lembrete, desde, ate).stream().findFirst();
    }

    /** O que vem depois de hoje, para os próximos dias. Aqui o de dia inteiro entra. */
    public List<CompromissoDoDia> proximosDias(int dias) {
        LocalDate amanha = LocalDate.now().plusDays(1);
        return ocorrenciasEntre(amanha.atStartOfDay(),
                amanha.plusDays(Math.max(0, dias - 1)).atTime(23, 59, 59), true);
    }

    private List<CompromissoDoDia> ocorrenciasEntre(LocalDateTime de, LocalDateTime ate,
                                                    boolean comDiaInteiro) {
        List<CompromissoDoDia> lista = new ArrayList<>();
        for (Lembrete lembrete : lembreteDao.listarAtivos()) {
            for (LocalDateTime quando : CalculadoraOcorrencias.entre(lembrete, de, ate)) {
                lista.add(CompromissoDoDia.de(lembrete, quando));
            }
        }
        LocalDateTime agora = LocalDateTime.now();
        for (AgendaService.EventoNaAgenda e : AgendaService.eventosEntre(de, ate)) {
            Evento ev = e.evento();
            if (ev.diaInteiro() && !comDiaInteiro) {
                continue;
            }
            // O que começou antes do intervalo só entra se ainda está
            // acontecendo; as férias que começaram ontem não se repetem a
            // cada dia da lista.
            if (ev.inicio().isBefore(de) && (ev.diaInteiro() || !ev.emAndamento(agora))) {
                continue;
            }
            lista.add(CompromissoDoDia.de(e));
        }
        lista.sort(Comparator.comparing(CompromissoDoDia::quando));
        return lista;
    }

    /** Quantos compromissos ainda estão por vir hoje. */
    public long quantosFaltamHoje() {
        return agendaDeHoje().stream().filter(c -> !c.jaPassou()).count();
    }

    // ------------------------------------------------------------- recados

    public List<Recado> listarRecados() {
        return recadoDao.listar();
    }

    public Recado salvarRecado(Recado recado) {
        String erro = recado.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (recado.getId() == null) {
            recado.setOrdem(recadoDao.proximaOrdem());
        }
        Recado salvo = recadoDao.salvar(recado);
        EventBus.publicar(new RecadosMudaram());
        return salvo;
    }

    public void excluirRecado(long id) {
        recadoDao.excluir(id);
        EventBus.publicar(new RecadosMudaram());
    }

    /**
     * Move um recado para outra posição.
     *
     * Recebe a lista já na ordem desejada pela tela; aqui só se grava. Fazer
     * a conta de posições aqui, e não na interface, mantém a tela boba e a
     * regra em um lugar só.
     */
    public void reordenar(List<Recado> naNovaOrdem) {
        recadoDao.gravarOrdem(naNovaOrdem);
        EventBus.publicar(new RecadosMudaram());
    }

    /** Troca a cor sem abrir o editor. */
    public void trocarCor(Recado recado, CorRecado cor) {
        recado.setCor(cor);
        recadoDao.salvar(recado);
        EventBus.publicar(new RecadosMudaram());
    }
}
