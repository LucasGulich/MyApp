package br.com.myapp.modules.uteis.youtube;

import java.util.Optional;

/**
 * Uma linha da saída do yt-dlp, já entendida.
 *
 * <p>O MyApp pede ao yt-dlp que escreva o progresso num formato próprio,
 * separado por "|" (ver {@link PedidoDownload#comando}), e esta classe lê de
 * volta. Valor que o yt-dlp não sabe vem como "NA" e vira -1.
 */
public record EventoDownload(Tipo tipo, long baixado, long total, double velocidade, long faltaSegundos,
                             int item, int itens, String texto) {

    public enum Tipo {
        /** Bytes chegando. */
        BAIXANDO,
        /** Terminou um pedaço (o vídeo, depois o áudio). */
        PARTE_PRONTA,
        /** Começou uma etapa depois do download: juntar, converter, embutir capa. */
        ETAPA,
        /** O arquivo final, já no lugar. {@link #texto} é o caminho. */
        ARQUIVO,
        /** Uma linha "ERROR: ...". {@link #texto} é a mensagem, sem o prefixo. */
        ERRO
    }

    /** Fração de 0 a 1, ou -1 sem tamanho conhecido. */
    public double fracao() {
        return total > 0 ? Math.min(1.0, (double) baixado / total) : -1;
    }

    public static Optional<EventoDownload> ler(String linha) {
        if (linha == null) {
            return Optional.empty();
        }
        String l = linha.strip();
        if (l.startsWith(PedidoDownload.MARCA_ARQUIVO)) {
            return Optional.of(simples(Tipo.ARQUIVO, l.substring(PedidoDownload.MARCA_ARQUIVO.length())));
        }
        if (l.startsWith(PedidoDownload.MARCA_ETAPA)) {
            String[] p = l.substring(PedidoDownload.MARCA_ETAPA.length()).split("\\|", -1);
            if (p.length >= 2 && "started".equals(p[0])) {
                return Optional.of(simples(Tipo.ETAPA, p[1]));
            }
            return Optional.empty();
        }
        if (l.startsWith(PedidoDownload.MARCA_PROGRESSO)) {
            String[] p = l.substring(PedidoDownload.MARCA_PROGRESSO.length()).split("\\|", -1);
            if (p.length < 8) {
                return Optional.empty();
            }
            long baixado = inteiro(p[1]);
            long total = inteiro(p[2]) > 0 ? inteiro(p[2]) : inteiro(p[3]);
            Tipo tipo = "finished".equals(p[0]) ? Tipo.PARTE_PRONTA : Tipo.BAIXANDO;
            return Optional.of(new EventoDownload(tipo, baixado, total, decimal(p[4]), inteiro(p[5]),
                    (int) inteiro(p[6]), (int) inteiro(p[7]), ""));
        }
        if (l.startsWith("ERROR:")) {
            return Optional.of(simples(Tipo.ERRO, l.substring(6).trim()));
        }
        return Optional.empty();
    }

    /**
     * O nome da etapa para a tela. O yt-dlp chama pelo nome da classe que faz
     * o trabalho; quem usa quer saber o que está acontecendo.
     */
    public static String descreverEtapa(String etapa) {
        if (etapa == null) {
            return "Finalizando…";
        }
        return switch (etapa) {
            case "Merger" -> "Juntando vídeo e áudio…";
            case "ExtractAudio" -> "Convertendo o áudio…";
            case "EmbedThumbnail" -> "Colocando a capa no arquivo…";
            case "Metadata", "FFmpegMetadata" -> "Gravando título e canal no arquivo…";
            case "VideoConvertor", "VideoRemuxer" -> "Convertendo o vídeo…";
            default -> "Finalizando…";
        };
    }

    private static EventoDownload simples(Tipo tipo, String texto) {
        return new EventoDownload(tipo, -1, -1, -1, -1, -1, -1, texto);
    }

    private static long inteiro(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static double decimal(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
