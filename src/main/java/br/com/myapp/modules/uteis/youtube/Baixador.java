package br.com.myapp.modules.uteis.youtube;

import br.com.myapp.core.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Roda o yt-dlp: lê as informações de um link e baixa.
 *
 * <p>Um download por vez. Os eventos chegam na thread do download — quem
 * desenha precisa passar para a thread da tela.
 */
public final class Baixador {

    /** Restos de download interrompido: ".part", ".ytdl" e os pedaços ".f137.mp4" antes de juntar. */
    private static final Pattern RESTO = Pattern.compile(".*(\\.part|\\.ytdl|\\.temp|\\.f\\d+\\.[a-z0-9]+(\\.part)?)$");

    private volatile Process processo;
    private volatile boolean cancelado;

    /** Lê título, canal, duração e resoluções. Bloqueia: chamar fora da tela. */
    public Midia informacoes(String link, boolean playlist) throws IOException, InterruptedException {
        Ferramentas.Local yt = Ferramentas.localizar(Ferramenta.YT_DLP)
                .orElseThrow(() -> new IOException("O yt-dlp não está instalado."));
        Ferramentas.Resultado r = Ferramentas.rodar(
                PedidoDownload.comandoInformacoes(yt.caminho(), denoDoMyApp(), link, playlist),
                Duration.ofMinutes(2));
        String saida = r.saida().trim();
        int inicio = saida.indexOf('{');
        if (r.codigo() != 0 || inicio < 0) {
            throw new IOException(mensagemDeErro(saida));
        }
        return Midia.doJson(saida.substring(inicio));
    }

    /**
     * Baixa e devolve os arquivos prontos (mais de um numa playlist).
     *
     * @throws InterruptedException se foi cancelado
     */
    public List<Path> baixar(PedidoDownload pedido, Consumer<EventoDownload> aoEvento)
            throws IOException, InterruptedException {
        Ferramentas.Local yt = Ferramentas.localizar(Ferramenta.YT_DLP)
                .orElseThrow(() -> new IOException("O yt-dlp não está instalado."));
        Optional<Path> ffmpeg = Ferramentas.localizar(Ferramenta.FFMPEG)
                .filter(Ferramentas.Local::doMyApp)
                .map(l -> l.caminho().getParent());

        Files.createDirectories(pedido.pasta());
        long inicio = System.currentTimeMillis();
        cancelado = false;

        List<String> comando = pedido.comando(yt.caminho(), ffmpeg, denoDoMyApp());
        Log.info("Baixando com o yt-dlp: " + pedido.tipo() + " → " + pedido.pasta());
        processo = Ferramentas.processo(comando).start();
        // Fechar o MyApp no meio não pode deixar o yt-dlp baixando escondido.
        Thread aoFechar = new Thread(this::cancelar, "encerrar-yt-dlp");
        Runtime.getRuntime().addShutdownHook(aoFechar);

        List<Path> prontos = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = in.readLine()) != null) {
                Optional<EventoDownload> evento = EventoDownload.ler(linha);
                if (evento.isEmpty()) {
                    continue;
                }
                EventoDownload e = evento.get();
                if (e.tipo() == EventoDownload.Tipo.ARQUIVO) {
                    prontos.add(Path.of(e.texto()));
                } else if (e.tipo() == EventoDownload.Tipo.ERRO) {
                    erros.add(e.texto());
                }
                aoEvento.accept(e);
            }
        }
        int codigo = processo.waitFor();
        processo = null;
        try {
            Runtime.getRuntime().removeShutdownHook(aoFechar);
        } catch (IllegalStateException jaFechando) {
            // O app já está fechando: o gancho roda e não acha processo.
        }

        if (cancelado) {
            limparRestos(pedido.pasta(), inicio);
            throw new InterruptedException("Download cancelado.");
        }
        // Playlist com um vídeo indisponível termina com erro, mas os outros
        // vieram: o que chegou vale, e o erro vira aviso na tela.
        if (prontos.isEmpty() && (codigo != 0 || !erros.isEmpty())) {
            throw new IOException(erros.isEmpty()
                    ? "O yt-dlp parou sem baixar nada (código " + codigo + ")."
                    : traduzir(erros.get(erros.size() - 1)));
        }
        return prontos;
    }

    public void cancelar() {
        cancelado = true;
        Process p = processo;
        if (p != null) {
            Ferramentas.encerrar(p);
        }
    }

    public boolean emAndamento() {
        return processo != null;
    }

    // -------------------------------------------------------------- apoio

    /**
     * O Deno do MyApp, quando é ele que existe. O do PATH o yt-dlp acha
     * sozinho; o da pasta do MyApp precisa ser apontado.
     */
    private static Optional<Path> denoDoMyApp() {
        return Ferramentas.localizar(Ferramenta.DENO)
                .filter(Ferramentas.Local::doMyApp)
                .map(Ferramentas.Local::caminho);
    }

    /**
     * Apaga só o que este download deixou pela metade: arquivos com cara de
     * resto e criados depois de ele começar. Um vídeo pronto que já estava
     * na pasta não é tocado.
     */
    private static void limparRestos(Path pasta, long desde) {
        try (Stream<Path> arquivos = Files.walk(pasta, 2)) {
            arquivos.filter(Files::isRegularFile)
                    .filter(p -> RESTO.matcher(p.getFileName().toString().toLowerCase()).matches())
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() >= desde - 2000;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            Log.aviso("Não apaguei o resto de download " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            Log.aviso("Falha ao limpar restos de download: " + e.getMessage());
        }
    }

    static boolean ehResto(String nomeArquivo) {
        return RESTO.matcher(nomeArquivo.toLowerCase()).matches();
    }

    private static String mensagemDeErro(String saida) {
        String ultimaErro = null;
        for (String linha : saida.split("\\R")) {
            if (linha.startsWith("ERROR:")) {
                ultimaErro = linha.substring(6).trim();
            }
        }
        return ultimaErro != null ? traduzir(ultimaErro) : "O yt-dlp não conseguiu ler este link.";
    }

    /**
     * Os erros que mais aparecem, em português e com o que fazer. O resto
     * vai como o yt-dlp escreveu — melhor em inglês do que escondido.
     */
    static String traduzir(String erro) {
        String e = erro.toLowerCase();
        if (e.contains("is not a valid url") || e.contains("unsupported url")) {
            return "Este link não é de um site que o yt-dlp conhece.";
        }
        if (e.contains("video unavailable") || e.contains("private video")) {
            return "O vídeo está indisponível ou é privado.";
        }
        if (e.contains("sign in to confirm your age")) {
            return "O YouTube exige login para este vídeo (restrição de idade).";
        }
        if (e.contains("http error 403") || e.contains("requested format is not available")
                || e.contains("unable to extract") || e.contains("nsig") || e.contains("signature")) {
            return "O YouTube mudou algo e o yt-dlp ficou para trás. Clique em \"Atualizar\" e tente de novo.\n("
                    + erro + ")";
        }
        if (e.contains("getaddrinfo failed") || e.contains("timed out") || e.contains("connection")) {
            return "Sem conexão com o site. Confira a internet e tente de novo.";
        }
        if (e.contains("ffmpeg") && e.contains("not found")) {
            return "Falta o FFmpeg para juntar ou converter. Clique em \"Instalar\".";
        }
        return erro;
    }
}
