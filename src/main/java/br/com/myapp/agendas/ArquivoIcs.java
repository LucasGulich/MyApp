package br.com.myapp.agendas;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.DateProperty;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Um arquivo .ics lido e pronto para responder "o que acontece entre tal e
 * tal hora?".
 *
 * O trabalho pesado — recorrência, exceções, fuso horário — é da ical4j. O que
 * fica aqui é o que ela não resolve sozinha, e que faria a agenda mostrar
 * coisa errada:
 *
 *  - <b>ocorrência remarcada</b>: no .ics, a "Daily de terça movida para as
 *    10h" é um evento à parte que aponta para a ocorrência original (pelo
 *    RECURRENCE-ID). A biblioteca continua gerando a das 9h a partir da
 *    série; sem tirá-la, a reunião apareceria duas vezes;
 *  - <b>cancelados</b>, que o Google mantém no arquivo;
 *  - <b>convites recusados</b>, se a agenda pedir para escondê-los;
 *  - o <b>link da reunião</b>, procurado no campo próprio do Google, no local
 *    e na descrição;
 *  - a <b>descrição</b> limpa do bloco de instruções que o Google acrescenta
 *    a todo evento com Meet.
 */
public final class ArquivoIcs {

    private static final Pattern LINK_REUNIAO = Pattern.compile(
            "https://(?:meet\\.google\\.com|[\\w.-]*zoom\\.us|teams\\.microsoft\\.com|teams\\.live\\.com)"
                    + "/[^\\s<>\"']+");

    /** Onde começa o bloco "Participe com o Google Meet…" da descrição. */
    private static final String MARCA_BLOCO_MEET = "-::~:~::~";

    private final Calendar calendario;
    private final String dono;

    /**
     * @throws IllegalArgumentException se o texto não for uma agenda
     */
    public ArquivoIcs(String conteudo) {
        if (conteudo == null || !conteudo.contains("BEGIN:VCALENDAR")) {
            throw new IllegalArgumentException(
                    "O endereço não devolveu uma agenda. Confira se copiou o "
                            + "\"Endereço secreto no formato iCal\", e não o endereço público.");
        }
        try {
            this.calendario = new CalendarBuilder().build(new StringReader(conteudo));
        } catch (ParserException | IOException | RuntimeException e) {
            throw new IllegalArgumentException("O arquivo da agenda veio com defeito e não pôde ser lido.", e);
        }
        this.dono = calendario.getProperty("X-WR-CALNAME")
                .map(Property::getValue)
                .filter(v -> v.contains("@"))
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    /** Quantos eventos (séries contam uma vez) o arquivo traz. */
    public int quantidadeDeEventos() {
        return eventos().size();
    }

    /**
     * Ocorrências que tocam o intervalo, em ordem de início.
     *
     * "Tocar" inclui o que começou antes e ainda não acabou: a reunião das
     * 13h às 15h aparece numa consulta a partir das 14h.
     */
    public List<Evento> eventosEntre(LocalDateTime de, LocalDateTime ate, boolean ocultarRecusados) {
        ZoneId zona = ZoneId.systemDefault();
        Period<ZonedDateTime> janela = new Period<>(de.atZone(zona), ate.atZone(zona));

        List<VEvent> todos = eventos();

        // Ocorrências que foram remarcadas ou canceladas uma a uma. A série
        // não pode gerá-las de novo.
        Set<String> substituidas = new HashSet<>();
        for (VEvent e : todos) {
            Optional<LocalDateTime> original = data(e, Property.RECURRENCE_ID);
            if (original.isPresent()) {
                substituidas.add(chave(uid(e), original.get()));
            }
        }

        List<Evento> resultado = new ArrayList<>();
        for (VEvent e : todos) {
            if (cancelado(e) || (ocultarRecusados && recusado(e))) {
                continue;
            }
            boolean ehSerie = e.getProperty(Property.RRULE).isPresent()
                    || e.getProperty(Property.RDATE).isPresent();
            boolean diaInteiro = data(e, Property.DTSTART).isPresent()
                    && temporal(e, Property.DTSTART) instanceof LocalDate;

            for (Period<Temporal> p : ocorrencias(e, janela)) {
                LocalDateTime inicio = paraLocal(p.getStart());
                if (ehSerie && substituidas.contains(chave(uid(e), inicio))) {
                    continue;
                }
                LocalDateTime fim = p.getEnd() == null ? inicio : paraLocal(p.getEnd());
                if (!fim.isAfter(inicio)) {
                    fim = diaInteiro ? inicio.plusDays(1) : inicio;
                }
                // A ical4j devolve o que encosta na janela; o que terminou
                // exatamente no início dela não interessa.
                if (fim.isAfter(de) || inicio.equals(de)) {
                    resultado.add(montar(e, inicio, fim, diaInteiro));
                }
            }
        }
        resultado.sort(Comparator.comparing(Evento::inicio).thenComparing(Evento::titulo));
        return resultado;
    }

    // ------------------------------------------------------------ internos

    private List<VEvent> eventos() {
        return calendario.getComponents(Component.VEVENT);
    }

    private Set<Period<Temporal>> ocorrencias(VEvent e, Period<ZonedDateTime> janela) {
        try {
            return e.calculateRecurrenceSet(janela);
        } catch (RuntimeException falha) {
            // Um evento com regra torta não pode esvaziar a agenda inteira.
            return Set.of();
        }
    }

    private Evento montar(VEvent e, LocalDateTime inicio, LocalDateTime fim, boolean diaInteiro) {
        String titulo = texto(e, Property.SUMMARY);
        String local = texto(e, Property.LOCATION);
        String link = linkDaReuniao(e);
        String descricao = limparDescricao(texto(e, Property.DESCRIPTION));
        if (link != null) {
            // O link vira o botão "Entrar"; repetido no texto, só ocupa espaço.
            descricao = descricao.replace(link, "").replaceAll("\n{3,}", "\n\n").strip();
        }
        return new Evento(uid(e), titulo.isBlank() ? "(sem título)" : titulo, descricao, local,
                inicio, fim, diaInteiro, link);
    }

    private boolean cancelado(VEvent e) {
        return "CANCELLED".equalsIgnoreCase(texto(e, Property.STATUS));
    }

    /** Você recusou o convite? Só dá para saber quando o .ics diz de quem é a agenda. */
    private boolean recusado(VEvent e) {
        if (dono == null) {
            return false;
        }
        List<Attendee> convidados = e.getProperties(Property.ATTENDEE);
        for (Attendee a : convidados) {
            String endereco = a.getCalAddress() == null ? "" : a.getCalAddress().toString();
            if (endereco.toLowerCase(Locale.ROOT).replace("mailto:", "").equals(dono)) {
                return a.getParameter(Parameter.PARTSTAT)
                        .map(p -> PartStat.DECLINED.getValue().equalsIgnoreCase(((Parameter) p).getValue()))
                        .orElse(false);
            }
        }
        return false;
    }

    private String linkDaReuniao(VEvent e) {
        for (String campo : List.of("X-GOOGLE-CONFERENCE", Property.LOCATION, Property.DESCRIPTION,
                Property.URL)) {
            Matcher m = LINK_REUNIAO.matcher(texto(e, campo));
            if (m.find()) {
                return m.group().replaceAll("[).,;]+$", "");
            }
        }
        return null;
    }

    /**
     * Tira da descrição o que não é do evento: o bloco que o Google cola em
     * toda reunião com Meet ("Participe com o Google Meet… -::~:~::~") e as
     * marcas de HTML que a versão web às vezes deixa.
     */
    static String limparDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "";
        }
        String texto = descricao;
        int bloco = texto.indexOf(MARCA_BLOCO_MEET);
        if (bloco >= 0) {
            texto = texto.substring(0, bloco);
        }
        texto = texto.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return texto.replaceAll("\n{3,}", "\n\n").strip();
    }

    private static String uid(VEvent e) {
        return e.getUid().map(Property::getValue).orElse("");
    }

    private static String texto(VEvent e, String propriedade) {
        return e.getProperty(propriedade).map(Property::getValue).orElse("");
    }

    private static Temporal temporal(VEvent e, String propriedade) {
        return e.getProperty(propriedade)
                .filter(p -> p instanceof DateProperty)
                .map(p -> (Temporal) ((DateProperty<?>) p).getDate())
                .orElse(null);
    }

    private static Optional<LocalDateTime> data(VEvent e, String propriedade) {
        return Optional.ofNullable(temporal(e, propriedade)).map(ArquivoIcs::paraLocal);
    }

    private static String chave(String uid, LocalDateTime quando) {
        return uid + "|" + quando;
    }

    /** Qualquer forma de data do .ics, trazida para o relógio deste computador. */
    static LocalDateTime paraLocal(Temporal t) {
        ZoneId aqui = ZoneId.systemDefault();
        if (t instanceof ZonedDateTime z) {
            return z.withZoneSameInstant(aqui).toLocalDateTime();
        }
        if (t instanceof OffsetDateTime o) {
            return o.atZoneSameInstant(aqui).toLocalDateTime();
        }
        if (t instanceof Instant i) {
            return LocalDateTime.ofInstant(i, aqui);
        }
        if (t instanceof LocalDateTime l) {
            return l;   // hora "flutuante": vale a do relógio, onde quer que ele esteja
        }
        if (t instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        throw new IllegalArgumentException("Data em formato desconhecido: " + t);
    }
}
