package br.com.myapp.modules.uteis.youtube;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O que a pessoa escolheu na tela, traduzido para a linha de comando do yt-dlp.
 *
 * <p>Fica separado da tela para dar para testar: cada escolha tem de virar
 * exatamente a opção certa, e um erro aqui só apareceria no arquivo errado.
 *
 * @param alturaMaxima 0 para a melhor disponível; 1080, 720... para limitar
 */
public record PedidoDownload(String link, Tipo tipo, int alturaMaxima,
                             FormatoAudio formatoAudio, String qualidadeAudio,
                             boolean playlist, boolean embutir, Path pasta) {

    public enum Tipo { VIDEO, AUDIO }

    public enum FormatoAudio {
        MP3("mp3"), M4A("m4a");

        final String opcao;

        FormatoAudio(String opcao) {
            this.opcao = opcao;
        }
    }

    /** Marcas no começo das linhas que o MyApp pede para o yt-dlp escrever. */
    static final String MARCA_PROGRESSO = "MYAPP|";
    static final String MARCA_ETAPA = "MYAPP-PP|";
    static final String MARCA_ARQUIVO = "MYAPP-ARQUIVO|";

    /**
     * A linha de comando inteira.
     *
     * @param ffmpeg pasta do FFmpeg, quando é a cópia do MyApp (a do PATH o
     *               yt-dlp acha sozinho)
     * @param deno   o deno.exe, idem
     */
    public List<String> comando(Path ytDlp, Optional<Path> ffmpeg, Optional<Path> deno) {
        List<String> c = new ArrayList<>(List.of(
                ytDlp.toString(),
                "--encoding", "utf-8",
                // Uma linha por atualização, sem o resto da conversa do yt-dlp:
                // o que a tela precisa vem nas linhas marcadas abaixo.
                "--newline", "--progress", "--quiet", "--no-warnings",
                // A data do arquivo é a do download, e não a de publicação do
                // vídeo — senão ele some no fim da pasta Downloads ordenada por data.
                "--no-mtime",
                "--progress-template", "download:" + MARCA_PROGRESSO
                        + "%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s"
                        + "|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s"
                        + "|%(info.playlist_index)s|%(info.n_entries)s",
                "--progress-template", "postprocess:" + MARCA_ETAPA
                        + "%(progress.status)s|%(progress.postprocessor)s",
                "--print", "after_move:" + MARCA_ARQUIVO + "%(filepath)s",
                "-P", pasta.toString(),
                "-o", playlist
                        ? "%(playlist_title)s/%(playlist_index)03d - %(title)s.%(ext)s"
                        : "%(title)s.%(ext)s",
                playlist ? "--yes-playlist" : "--no-playlist"));

        ffmpeg.ifPresent(p -> c.addAll(List.of("--ffmpeg-location", p.toString())));
        deno.ifPresent(p -> c.addAll(List.of("--js-runtimes", "deno:" + p)));

        if (tipo == Tipo.VIDEO) {
            String limite = alturaMaxima > 0 ? "[height<=" + alturaMaxima + "]" : "";
            c.addAll(List.of(
                    "-f", "bv*" + limite + "+ba/b" + limite,
                    // Na mesma resolução, prefere H.264 com AAC: abre em qualquer
                    // player do Windows, inclusive os antigos.
                    "-S", "res,vcodec:h264,acodec:m4a",
                    "--merge-output-format", "mp4"));
        } else {
            c.addAll(List.of(
                    "-x", "--audio-format", formatoAudio.opcao,
                    "--audio-quality", qualidadeAudio));
        }

        if (embutir) {
            c.addAll(List.of("--embed-metadata", "--embed-thumbnail"));
        }

        // "--" antes do link: um link que comece com "-" não vira opção.
        c.add("--");
        c.add(link.trim());
        return c;
    }

    /** O comando que só lê as informações do link, sem baixar nada. */
    public static List<String> comandoInformacoes(Path ytDlp, Optional<Path> deno, String link, boolean playlist) {
        List<String> c = new ArrayList<>(List.of(
                ytDlp.toString(), "--encoding", "utf-8", "--no-warnings",
                "-J", "--flat-playlist", playlist ? "--yes-playlist" : "--no-playlist"));
        deno.ifPresent(p -> c.addAll(List.of("--js-runtimes", "deno:" + p)));
        c.add("--");
        c.add(link.trim());
        return c;
    }

    /** Parece um link que o yt-dlp aceitaria? Só confere o começo. */
    public static boolean pareceLink(String texto) {
        if (texto == null) {
            return false;
        }
        String t = texto.trim().toLowerCase();
        return (t.startsWith("https://") || t.startsWith("http://")) && t.length() > 12 && !t.contains(" ");
    }
}
