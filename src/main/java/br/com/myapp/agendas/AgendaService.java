package br.com.myapp.agendas;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.security.SecurityService;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agendas externas: cadastro, sincronização e consulta dos eventos.
 *
 * <h3>Sincronização</h3>
 * A cada {@value #MINUTOS_ENTRE_SINCRONIZACOES} minutos, ao destrancar e ao
 * salvar uma agenda, o MyApp baixa o .ics de cada uma pelo endereço secreto.
 * Se falhar (sem rede, Google fora), a última cópia boa continua valendo e o
 * erro fica registrado para a tela de configurações mostrar.
 *
 * <h3>O endereço secreto na memória</h3>
 * No banco ele vai cifrado, e para decifrar é preciso a chave — que só existe
 * com o aplicativo destrancado. Ao destrancar, os endereços são decifrados e
 * <b>ficam na memória até o aplicativo fechar</b>, mesmo depois de trancar.
 * Sem isso, a agenda pararia de sincronizar a cada bloqueio por inatividade,
 * ou seja, quase o dia inteiro. Ver a decisão 43.
 *
 * <h3>Consulta</h3>
 * Ler o .ics e expandir as recorrências custa caro para repetir a cada minuto.
 * Por isso cada agenda é calculada uma vez para uma janela larga (de ontem a
 * {@value #DIAS_DA_JANELA} dias à frente) e as consultas só filtram essa
 * lista. A janela é refeita quando o dia vira ou quando chega conteúdo novo.
 */
public final class AgendaService {

    /** Publicado quando agendas ou eventos mudam, para as telas se atualizarem. */
    public record AgendasMudaram() {
    }

    /** Um evento junto da agenda de onde veio (cor, nome, proteção). */
    public record EventoNaAgenda(Agenda agenda, Evento evento) {
    }

    /** Resposta do botão "Testar". */
    public record ResultadoTeste(int eventos, Optional<Evento> proximo) {
    }

    public static final int MINUTOS_ENTRE_SINCRONIZACOES = 15;
    static final int DIAS_DA_JANELA = 40;

    private static final AgendaDao DAO = new AgendaDao();

    /** Endereços já decifrados. Ver a explicação da classe. */
    private static final Map<Long, String> LINKS = new ConcurrentHashMap<>();

    /** Arquivo lido e ocorrências já calculadas, por agenda. */
    private static final Map<Long, Calculada> CACHE = new ConcurrentHashMap<>();

    private static ScheduledExecutorService executor;
    private static HttpClient http;

    /**
     * O arquivo lido, as ocorrências da janela larga e as consultas avulsas
     * fora dela (a visão de ano pede o ano inteiro). Tudo some junto quando a
     * agenda sincroniza ou o dia vira, porque a Calculada inteira é trocada.
     */
    private record Calculada(ArquivoIcs arquivo, boolean ocultarRecusados,
                             LocalDateTime de, LocalDateTime ate, List<Evento> eventos,
                             Map<String, List<Evento>> avulsas) {

        Calculada(ArquivoIcs arquivo, boolean ocultarRecusados,
                  LocalDateTime de, LocalDateTime ate, List<Evento> eventos) {
            this(arquivo, ocultarRecusados, de, ate, eventos, new ConcurrentHashMap<>());
        }
    }

    /** Quantas consultas avulsas cada agenda guarda antes de esquecer as antigas. */
    private static final int MAXIMO_DE_AVULSAS = 12;

    private AgendaService() {
    }

    // -------------------------------------------------------------- ciclo

    /** Liga a sincronização periódica. A primeira acontece logo em seguida. */
    public static synchronized void iniciar() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread t = new Thread(tarefa, "agendas");
            t.setDaemon(true);
            return t;
        });
        EventBus.ouvir(SecurityService.EstadoMudou.class, e -> {
            if (e.destrancado()) {
                sincronizarTodasAgora();
            }
        });
        executor.scheduleWithFixedDelay(AgendaService::sincronizarTodas,
                5, MINUTOS_ENTRE_SINCRONIZACOES * 60L, TimeUnit.SECONDS);
        Log.info("Sincronização de agendas ativa (a cada " + MINUTOS_ENTRE_SINCRONIZACOES + " min).");
    }

    public static synchronized void parar() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        LINKS.clear();
        CACHE.clear();
    }

    /** Esquece tudo o que está na memória. Usado pelos testes ao trocar de banco. */
    public static void redefinir() {
        LINKS.clear();
        CACHE.clear();
    }

    // ------------------------------------------------------------ cadastro

    public static List<Agenda> listar() {
        return DAO.listar();
    }

    public static Optional<Agenda> porId(long id) {
        return DAO.porId(id);
    }

    /** O endereço secreto, para o formulário de edição. Vazio se trancado. */
    public static Optional<String> link(long id) {
        return Optional.ofNullable(LINKS.get(id)).or(() -> DAO.lerLink(id));
    }

    /**
     * Valida, grava e já dispara a primeira sincronização.
     *
     * @throws IllegalArgumentException com a mensagem pronta para a tela
     */
    public static Agenda salvar(Agenda agenda) {
        String erro = agenda.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        agenda.setNome(agenda.getNome().trim());
        agenda.setLink(normalizar(agenda.getLink()));

        Agenda salva = DAO.salvar(agenda);
        LINKS.put(salva.getId(), salva.getLink());
        CACHE.remove(salva.getId());
        EventBus.publicar(new AgendasMudaram());
        sincronizarAgora(salva.getId());
        return salva;
    }

    /** Exclusão lógica: some da tela e para de sincronizar. */
    public static void excluir(long id) {
        DAO.excluir(id);
        LINKS.remove(id);
        CACHE.remove(id);
        EventBus.publicar(new AgendasMudaram());
    }

    // ------------------------------------------------------------ consulta

    /**
     * Eventos de todas as agendas que tocam o intervalo, em ordem de início.
     *
     * Agenda protegida aparece aqui mesmo com o aplicativo trancado, desde
     * que tenha sido lida antes: quem esconde o título é a tela, como faz com
     * o lembrete protegido.
     */
    public static List<EventoNaAgenda> eventosEntre(LocalDateTime de, LocalDateTime ate) {
        List<EventoNaAgenda> lista = new ArrayList<>();
        for (Agenda agenda : DAO.listar()) {
            for (Evento e : eventosDa(agenda, de, ate)) {
                lista.add(new EventoNaAgenda(agenda, e));
            }
        }
        lista.sort(Comparator.comparing((EventoNaAgenda e) -> e.evento().inicio())
                .thenComparing(e -> e.evento().titulo()));
        return lista;
    }

    /** Eventos de uma agenda só que tocam o intervalo. */
    public static List<Evento> eventosDaAgenda(Agenda agenda, LocalDateTime de, LocalDateTime ate) {
        return eventosDa(agenda, de, ate);
    }

    /** Quantos eventos da agenda acontecem de agora até tantos dias à frente. */
    public static int quantosNosProximosDias(Agenda agenda, int dias) {
        LocalDateTime agora = LocalDateTime.now();
        return eventosDa(agenda, agora, agora.plusDays(dias)).size();
    }

    private static List<Evento> eventosDa(Agenda agenda, LocalDateTime de, LocalDateTime ate) {
        Calculada c = calculada(agenda);
        if (c == null) {
            return List.of();
        }
        // Fora da janela calculada (o ano inteiro, um mês que já passou):
        // faz a conta direto e guarda, porque a tela redesenha o mesmo período
        // várias vezes.
        if (de.isBefore(c.de()) || ate.isAfter(c.ate())) {
            if (c.avulsas().size() >= MAXIMO_DE_AVULSAS) {
                c.avulsas().clear();
            }
            return c.avulsas().computeIfAbsent(de + "|" + ate,
                    k -> c.arquivo().eventosEntre(de, ate, agenda.isOcultarRecusados()));
        }
        return c.eventos().stream()
                .filter(e -> e.fim().isAfter(de) || e.inicio().equals(de))
                .filter(e -> e.inicio().isBefore(ate))
                .toList();
    }

    private static Calculada calculada(Agenda agenda) {
        LocalDateTime de = LocalDate.now().minusDays(1).atStartOfDay();
        Calculada c = CACHE.get(agenda.getId());

        boolean valeAinda = c != null
                && de.equals(c.de())
                && c.ocultarRecusados() == agenda.isOcultarRecusados();
        if (valeAinda) {
            return c;
        }

        ArquivoIcs arquivo = c != null ? c.arquivo() : ler(agenda.getId());
        if (arquivo == null) {
            return null;
        }
        LocalDateTime ate = de.plusDays(DIAS_DA_JANELA + 1L);
        Calculada nova = new Calculada(arquivo, agenda.isOcultarRecusados(), de, ate,
                arquivo.eventosEntre(de, ate, agenda.isOcultarRecusados()));
        CACHE.put(agenda.getId(), nova);
        return nova;
    }

    private static ArquivoIcs ler(long id) {
        Optional<String> conteudo = DAO.lerConteudo(id);
        if (conteudo.isEmpty()) {
            return null;
        }
        try {
            return new ArquivoIcs(conteudo.get());
        } catch (IllegalArgumentException e) {
            Log.aviso("A cópia guardada da agenda " + id + " não pôde ser lida: " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------- sincronizar

    /** Sincroniza tudo em segundo plano, sem esperar a próxima rodada. */
    public static void sincronizarTodasAgora() {
        ScheduledExecutorService e = executor;
        if (e != null) {
            e.execute(AgendaService::sincronizarTodas);
        }
    }

    /** Sincroniza uma agenda em segundo plano. */
    public static void sincronizarAgora(long id) {
        ScheduledExecutorService e = executor;
        if (e != null) {
            e.execute(() -> sincronizar(id));
        }
    }

    private static void sincronizarTodas() {
        try {
            for (Agenda agenda : DAO.listar()) {
                sincronizar(agenda.getId());
            }
        } catch (Exception e) {
            // Uma falha aqui não pode matar a thread: as rodadas seguintes
            // deixariam de acontecer sem nenhum sinal.
            Log.erro("Falha ao sincronizar as agendas", e);
        }
    }

    /**
     * Baixa e guarda a agenda agora, nesta thread.
     *
     * @return true se deu certo; false se falhou ou se ainda não há como
     *         saber o endereço (aplicativo nunca destrancado desde que abriu)
     */
    public static boolean sincronizar(long id) {
        // Pela lista das vivas, e não por porId: agenda excluída não sincroniza.
        Optional<Agenda> encontrada = DAO.listar().stream()
                .filter(a -> a.getId() == id)
                .findFirst();
        if (encontrada.isEmpty()) {
            return false;
        }
        Agenda agenda = encontrada.get();

        Optional<String> link = link(id);
        if (link.isEmpty()) {
            return false;   // espera o próximo destrancar
        }
        LINKS.put(id, link.get());

        try {
            String conteudo = baixar(link.get());
            ArquivoIcs arquivo = new ArquivoIcs(conteudo);
            LocalDateTime agora = LocalDateTime.now();

            if (agenda.isProtegida() && !SecurityService.estaDestrancado()) {
                DAO.marcarSincronizada(id, agora);
            } else {
                DAO.gravarSincronizacao(id, conteudo, agenda.isProtegida(), agora);
            }
            // Sem janela: a próxima consulta calcula a partir do arquivo novo.
            CACHE.put(id, new Calculada(arquivo, agenda.isOcultarRecusados(), null, null, List.of()));
            Log.info("Agenda " + id + " sincronizada: " + arquivo.quantidadeDeEventos() + " eventos.");
            EventBus.publicar(new AgendasMudaram());
            return true;
        } catch (Exception e) {
            String mensagem = mensagemDe(e);
            boolean erroNovo = !mensagem.equals(agenda.getUltimoErro());
            DAO.gravarErro(id, mensagem);
            Log.aviso("Agenda " + id + " não sincronizou: " + mensagem);
            if (erroNovo) {
                EventBus.publicar(new AgendasMudaram());
            }
            return false;
        }
    }

    /**
     * Baixa um endereço e conta o que vem nele, sem gravar nada. É o botão
     * "Testar" do formulário — roda na thread de quem chamar.
     *
     * @throws IllegalArgumentException com a mensagem pronta para a tela
     */
    public static ResultadoTeste testar(String link) {
        String normalizado = normalizar(link);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException("Cole o endereço secreto da agenda.");
        }
        try {
            ArquivoIcs arquivo = new ArquivoIcs(baixar(normalizado));
            LocalDateTime agora = LocalDateTime.now();
            Optional<Evento> proximo = arquivo.eventosEntre(agora, agora.plusDays(DIAS_DA_JANELA), true)
                    .stream()
                    .filter(e -> !e.inicio().isBefore(agora))
                    .findFirst();
            return new ResultadoTeste(arquivo.quantidadeDeEventos(), proximo);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(mensagemDe(e), e);
        }
    }

    // ---------------------------------------------------------------- rede

    private static String baixar(String link) throws IOException, InterruptedException {
        URI endereco;
        try {
            endereco = URI.create(link);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O endereço colado não é válido.");
        }
        if (!Agenda.enderecoAceito(link) || endereco.getHost() == null) {
            throw new IllegalArgumentException("O endereço precisa começar com https://.");
        }
        HttpRequest pedido = HttpRequest.newBuilder(endereco)
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "MyApp")
                .GET()
                .build();
        HttpResponse<String> resposta = cliente().send(pedido,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int codigo = resposta.statusCode();
        if (codigo == 404) {
            throw new IllegalArgumentException("O Google não encontrou esta agenda. Se você redefiniu "
                    + "o endereço secreto, cole o novo.");
        }
        if (codigo == 401 || codigo == 403) {
            throw new IllegalArgumentException("O Google recusou o acesso. O endereço secreto pode ter "
                    + "sido redefinido, ou desativado pela empresa.");
        }
        if (codigo < 200 || codigo >= 300) {
            throw new IllegalArgumentException("O Google respondeu com erro (código " + codigo
                    + "). Tente de novo daqui a pouco.");
        }
        return resposta.body();
    }

    private static synchronized HttpClient cliente() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return http;
    }

    /** "webcal://" é o mesmo endereço por https; espaços nas pontas vêm do colar. */
    static String normalizar(String link) {
        if (link == null) {
            return "";
        }
        String l = link.trim();
        if (l.regionMatches(true, 0, "webcal://", 0, 9)) {
            l = "https://" + l.substring(9);
        }
        return l;
    }

    /** Falha traduzida para quem vai ler na tela. Nunca contém o endereço. */
    private static String mensagemDe(Exception e) {
        // As mensagens próprias (IllegalArgumentException) já nascem prontas e
        // sem o endereço: o erro de URI inválida é trocado em baixar().
        if (e instanceof IllegalArgumentException && e.getMessage() != null) {
            return e.getMessage();
        }
        if (e instanceof HttpConnectTimeoutException || e instanceof HttpTimeoutException) {
            return "O Google demorou demais para responder.";
        }
        if (e instanceof ConnectException || e instanceof IOException) {
            return "Sem conexão com o Google.";
        }
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "A sincronização foi interrompida.";
        }
        return "Não foi possível sincronizar a agenda.";
    }
}
