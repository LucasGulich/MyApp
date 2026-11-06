package br.com.myapp.modules.uteis.youtube;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Acha, instala e atualiza os programas do baixador.
 *
 * <p><b>Onde procurar</b>, nesta ordem: a cópia do próprio MyApp, em
 * {@link AppPaths#pastaFerramentas()}; depois o que estiver no PATH do
 * Windows — é onde fica quem já instalou pelo {@code winget}, e não faz
 * sentido baixar de novo o que já está na máquina.
 *
 * <p><b>Instalar</b> sempre põe na pasta do MyApp, sem pedir administrador e
 * sem mexer no PATH. <b>Atualizar</b> é o {@code yt-dlp -U}: o yt-dlp é o
 * único que envelhece rápido, porque o YouTube muda e ele corre atrás.
 *
 * <p>Tudo aqui bloqueia (rede e processos): chamar fora da thread da tela.
 */
public final class Ferramentas {

    /** Um programa encontrado, e se a cópia é do MyApp ou do Windows. */
    public record Local(Ferramenta ferramenta, Path caminho, boolean doMyApp) {
    }

    /** Saída de um processo que já terminou. */
    record Resultado(int codigo, String saida) {
    }

    private static HttpClient http;

    private Ferramentas() {
    }

    // ---------------------------------------------------------- localizar

    public static Optional<Local> localizar(Ferramenta f) {
        Path propria = AppPaths.pastaFerramentas().resolve(f.executavel);
        if (Files.isRegularFile(propria)) {
            return Optional.of(new Local(f, propria, true));
        }
        return noPath(f.executavel).map(p -> new Local(f, p, false));
    }

    /** As que faltam, na ordem da instalação. */
    public static List<Ferramenta> faltando() {
        List<Ferramenta> faltam = new ArrayList<>();
        for (Ferramenta f : Ferramenta.values()) {
            if (localizar(f).isEmpty()) {
                faltam.add(f);
            }
        }
        return faltam;
    }

    private static Optional<Path> noPath(String executavel) {
        try {
            Resultado r = rodar(List.of("where", executavel), Duration.ofSeconds(10));
            if (r.codigo() == 0) {
                for (String linha : r.saida().split("\\R")) {
                    Path p = Paths.get(linha.trim());
                    if (!linha.isBlank() && Files.isRegularFile(p)) {
                        return Optional.of(p);
                    }
                }
            }
        } catch (Exception e) {
            Log.aviso("Falha ao procurar " + executavel + " no PATH: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** "2026.08.19", ou vazio se o yt-dlp não respondeu. */
    public static Optional<String> versaoYtDlp() {
        return localizar(Ferramenta.YT_DLP).flatMap(l -> {
            try {
                Resultado r = rodar(List.of(l.caminho().toString(), "--version"), Duration.ofSeconds(30));
                String v = r.saida().trim();
                return r.codigo() == 0 && !v.isEmpty() ? Optional.of(v.lines().reduce((a, b) -> b).orElse(v))
                        : Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    // ---------------------------------------------------------- atualizar

    /**
     * {@code yt-dlp -U}. Devolve a última linha que ele escreveu — "yt-dlp is
     * up to date (...)" ou "Updated yt-dlp to ..." —, já em português quando
     * é um dos casos conhecidos.
     */
    public static String atualizarYtDlp() throws IOException, InterruptedException {
        Local l = localizar(Ferramenta.YT_DLP)
                .orElseThrow(() -> new IOException("O yt-dlp não está instalado."));
        Resultado r = rodar(List.of(l.caminho().toString(), "-U"), Duration.ofMinutes(3));
        String ultima = r.saida().lines().map(String::trim).filter(s -> !s.isEmpty())
                .reduce((a, b) -> b).orElse("");
        if (ultima.contains("is up to date")) {
            return "O yt-dlp já está na versão mais nova.";
        }
        if (ultima.startsWith("Updated yt-dlp to")) {
            return "yt-dlp atualizado para a versão " + ultima.replaceAll(".*to\\s+", "").replaceAll("\\s.*", "") + ".";
        }
        if (r.codigo() != 0) {
            throw new IOException(ultima.isEmpty() ? "O yt-dlp não conseguiu se atualizar." : ultima);
        }
        return ultima;
    }

    // ----------------------------------------------------------- instalar

    /** Progresso de uma instalação: o que está baixando e quanto já veio. */
    public interface Progresso {
        void atualizar(Ferramenta ferramenta, long baixado, long total);
    }

    /**
     * Baixa, confere a soma e põe na pasta do MyApp.
     *
     * <p>O arquivo baixa para um temporário na própria pasta e só vira
     * {@code .exe} depois de a soma bater. Se o download for cancelado ou a
     * soma não bater, o temporário é apagado e nada do que já existia muda.
     */
    public static void instalar(Ferramenta f, Progresso progresso, BooleanSupplier cancelado)
            throws IOException, InterruptedException {
        Path pasta = AppPaths.pastaFerramentas();
        String somas = baixarTexto(f.urlSomas);
        String esperada = f.somaEsperada(somas).orElseThrow(() ->
                new IOException("Não achei a soma de conferência do " + f.nome + " na página oficial."));

        Path temporario = pasta.resolve(f.arquivo + ".baixando");
        try {
            String obtida = baixarArquivo(f, temporario, progresso, cancelado);
            if (!obtida.equalsIgnoreCase(esperada)) {
                throw new IOException("O arquivo do " + f.nome + " chegou diferente do publicado "
                        + "(soma não confere). Nada foi instalado; tente de novo.");
            }
            if (f.doZip.isEmpty()) {
                Files.move(temporario, pasta.resolve(f.executavel), StandardCopyOption.REPLACE_EXISTING);
            } else {
                extrair(temporario, pasta, f.doZip);
            }
            Log.info(f.nome + " instalado em " + pasta);
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private static String baixarArquivo(Ferramenta f, Path destino, Progresso progresso, BooleanSupplier cancelado)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> resposta = cliente().send(pedido(f.url), HttpResponse.BodyHandlers.ofInputStream());
        if (resposta.statusCode() != 200) {
            resposta.body().close();
            throw new IOException("O GitHub respondeu com erro (código " + resposta.statusCode()
                    + ") ao baixar o " + f.nome + ".");
        }
        long total = resposta.headers().firstValueAsLong("content-length").orElse(-1);
        MessageDigest sha = sha256();
        try (InputStream in = resposta.body(); OutputStream out = Files.newOutputStream(destino)) {
            byte[] buffer = new byte[64 * 1024];
            long baixado = 0;
            long ultimoAviso = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                if (cancelado.getAsBoolean()) {
                    throw new InterruptedException("Instalação cancelada.");
                }
                out.write(buffer, 0, n);
                sha.update(buffer, 0, n);
                baixado += n;
                // Avisar a cada 256 KB basta para a barra andar lisa sem
                // inundar a tela de atualizações.
                if (baixado - ultimoAviso >= 256 * 1024) {
                    ultimoAviso = baixado;
                    progresso.atualizar(f, baixado, total);
                }
            }
            progresso.atualizar(f, baixado, total);
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    /**
     * Tira do .zip só os executáveis pedidos, pelo nome — onde quer que
     * estejam lá dentro (o FFmpeg vem em "ffmpeg-master.../bin/").
     */
    private static void extrair(Path zip, Path pasta, List<String> nomes) throws IOException {
        List<String> faltam = new ArrayList<>(nomes);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entrada;
            while ((entrada = in.getNextEntry()) != null) {
                String nome = entrada.getName().replace('\\', '/');
                String base = nome.substring(nome.lastIndexOf('/') + 1);
                if (entrada.isDirectory() || !faltam.contains(base)) {
                    continue;
                }
                // Só o nome-base vai para o disco: um nome com ".." dentro do
                // zip não consegue escapar da pasta.
                Path temp = pasta.resolve(base + ".extraindo");
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, pasta.resolve(base), StandardCopyOption.REPLACE_EXISTING);
                faltam.remove(base);
            }
        }
        if (!faltam.isEmpty()) {
            throw new IOException("O pacote baixado não tem " + String.join(", ", faltam) + ".");
        }
    }

    private static String baixarTexto(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = cliente().send(pedido(url), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IOException("Não consegui falar com o GitHub (código " + r.statusCode() + ").");
        }
        return r.body();
    }

    private static HttpRequest pedido(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "MyApp")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
    }

    private static synchronized HttpClient cliente() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return http;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ----------------------------------------------------------- processos

    /**
     * Roda um programa e espera terminar, com a saída e os erros juntos.
     *
     * <p>{@code PYTHONIOENCODING}: o yt-dlp é Python, e no Windows escreve na
     * página de código do console (cp1252) — título com acento ou emoji
     * chegaria trocado.
     */
    static Resultado rodar(List<String> comando, Duration limite) throws IOException, InterruptedException {
        Process p = processo(comando).start();
        byte[] saida;
        try (InputStream in = p.getInputStream()) {
            saida = in.readAllBytes();
        }
        if (!p.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
            encerrar(p);
            throw new IOException("O programa demorou demais para responder.");
        }
        return new Resultado(p.exitValue(), new String(saida, StandardCharsets.UTF_8));
    }

    static ProcessBuilder processo(List<String> comando) {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        return pb;
    }

    /**
     * Encerra o processo e os filhos dele. O yt-dlp.exe é um lançador que
     * abre outro processo (o Python de verdade), que por sua vez abre o
     * FFmpeg: matar só o primeiro deixaria os outros baixando escondidos.
     */
    static void encerrar(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }
}
