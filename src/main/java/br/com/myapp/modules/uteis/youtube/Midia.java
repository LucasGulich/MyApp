package br.com.myapp.modules.uteis.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

/**
 * O que o link aponta, para mostrar antes de baixar: título, canal, duração,
 * capa e as resoluções que existem de verdade.
 *
 * <p>Vem do {@code yt-dlp -J}, que descreve o vídeo em JSON sem baixar nada.
 *
 * @param alturas resoluções de vídeo disponíveis, da maior para a menor
 * @param itens   quantos vídeos, quando é playlist; 1 para um vídeo
 */
public record Midia(String titulo, String canal, long duracaoSegundos, String capa,
                    List<Integer> alturas, boolean playlist, int itens) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static Midia doJson(String json) throws IOException {
        JsonNode raiz = JSON.readTree(json);
        String titulo = texto(raiz, "title");

        if ("playlist".equals(texto(raiz, "_type"))) {
            JsonNode entradas = raiz.path("entries");
            int itens = entradas.isArray() ? entradas.size() : raiz.path("playlist_count").asInt(0);
            String capa = "";
            JsonNode capas = raiz.path("thumbnails");
            if (capas.isArray() && capas.size() > 0) {
                capa = texto(capas.get(capas.size() - 1), "url");
            }
            return new Midia(titulo, primeiro(raiz, "channel", "uploader"), -1, capa, List.of(), true, itens);
        }

        TreeSet<Integer> alturas = new TreeSet<>();
        for (JsonNode f : raiz.path("formats")) {
            int altura = f.path("height").asInt(0);
            if (altura > 0 && !"none".equals(texto(f, "vcodec"))) {
                alturas.add(altura);
            }
        }
        return new Midia(titulo, primeiro(raiz, "channel", "uploader"),
                raiz.path("duration").asLong(-1), texto(raiz, "thumbnail"),
                List.copyOf(alturas.descendingSet()), false, 1);
    }

    /** "3:45" ou "1:02:07"; vazio se não se sabe. */
    public String duracaoFormatada() {
        if (duracaoSegundos < 0) {
            return "";
        }
        long h = duracaoSegundos / 3600;
        long m = (duracaoSegundos % 3600) / 60;
        long s = duracaoSegundos % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** A maior resolução, ou 0 quando o link é só áudio ou não se sabe. */
    public int maiorAltura() {
        return alturas.isEmpty() ? 0 : alturas.get(0);
    }

    private static String primeiro(JsonNode no, String... campos) {
        for (String c : campos) {
            String v = texto(no, c);
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.path(campo);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }
}
