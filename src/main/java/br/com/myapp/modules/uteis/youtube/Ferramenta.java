package br.com.myapp.modules.uteis.youtube;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Os três programas de que o baixador depende, e de onde cada um vem.
 *
 * <p>Todos saem da página oficial de lançamentos no GitHub, sempre a versão
 * mais nova ({@code latest}), junto com a lista de somas SHA-256 que o próprio
 * projeto publica. A soma é conferida antes de o arquivo ir para a pasta: um
 * download cortado ou trocado no caminho não vira executável. Ver
 * {@code docs/UTEIS.md} e a decisão 49.
 */
public enum Ferramenta {

    YT_DLP("yt-dlp", "yt-dlp.exe",
            "Quem conversa com o YouTube e baixa de fato.",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS",
            "yt-dlp.exe", List.of(), 18),

    FFMPEG("FFmpeg", "ffmpeg.exe",
            "Junta o vídeo com o áudio e converte para MP3.",
            "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
            "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/checksums.sha256",
            "ffmpeg-master-latest-win64-gpl.zip", List.of("ffmpeg.exe", "ffprobe.exe"), 200),

    DENO("Deno", "deno.exe",
            "Roda o código que o YouTube exige para liberar os formatos.",
            "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip",
            "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip.sha256sum",
            "deno-x86_64-pc-windows-msvc.zip", List.of("deno.exe"), 43);

    private static final Pattern SHA256 = Pattern.compile("\\b([0-9a-fA-F]{64})\\b");

    public final String nome;
    public final String executavel;
    public final String papel;
    final String url;
    final String urlSomas;
    /** Nome do arquivo baixado, como aparece na lista de somas. */
    final String arquivo;
    /** O que tirar de dentro do .zip. Vazio: o download já é o executável. */
    final List<String> doZip;
    public final int tamanhoMb;

    Ferramenta(String nome, String executavel, String papel, String url, String urlSomas,
               String arquivo, List<String> doZip, int tamanhoMb) {
        this.nome = nome;
        this.executavel = executavel;
        this.papel = papel;
        this.url = url;
        this.urlSomas = urlSomas;
        this.arquivo = arquivo;
        this.doZip = doZip;
        this.tamanhoMb = tamanhoMb;
    }

    /**
     * A soma esperada para {@link #arquivo}, tirada da lista publicada.
     *
     * <p>Os projetos não escrevem a lista do mesmo jeito: yt-dlp e FFmpeg
     * usam "soma  nome", uma linha por arquivo; o Deno publica um arquivo por
     * download, no formato do {@code Get-FileHash} do PowerShell ("Hash : ..."),
     * com o nome do arquivo numa linha "Path" à parte. Por isso: primeiro a
     * linha que tem o nome e a soma juntos; sem nenhuma, vale a soma se ela
     * for a única do arquivo — mais de uma sem nome seria ambíguo, então nada.
     */
    public Optional<String> somaEsperada(String lista) {
        if (lista == null) {
            return Optional.empty();
        }
        for (String linha : lista.split("\\R")) {
            String l = linha.trim();
            if (l.endsWith(" " + arquivo) || l.endsWith("*" + arquivo)) {
                Matcher m = SHA256.matcher(l);
                if (m.find()) {
                    return Optional.of(m.group(1).toLowerCase());
                }
            }
        }
        Matcher m = SHA256.matcher(lista);
        if (m.find()) {
            String unica = m.group(1);
            if (!m.find()) {
                return Optional.of(unica.toLowerCase());
            }
        }
        return Optional.empty();
    }
}
