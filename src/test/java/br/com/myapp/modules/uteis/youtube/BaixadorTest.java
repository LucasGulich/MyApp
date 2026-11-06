package br.com.myapp.modules.uteis.youtube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O que o cancelar apaga e como os erros do yt-dlp chegam na tela. */
class BaixadorTest {

    @Test
    @DisplayName("Cancelar apaga só restos de download, nunca um arquivo pronto")
    void restos() {
        assertTrue(Baixador.ehResto("Me at the zoo.mp4.part"));
        assertTrue(Baixador.ehResto("Me at the zoo.f137.mp4"));
        assertTrue(Baixador.ehResto("Me at the zoo.f251.webm.part"));
        assertTrue(Baixador.ehResto("Me at the zoo.mp4.ytdl"));

        assertFalse(Baixador.ehResto("Me at the zoo.mp4"));
        assertFalse(Baixador.ehResto("Me at the zoo.mp3"));
        assertFalse(Baixador.ehResto("relatorio.final.pdf"));
    }

    @Test
    @DisplayName("Erros conhecidos viram português com o que fazer")
    void traduz() {
        assertEquals("O vídeo está indisponível ou é privado.",
                Baixador.traduzir("[youtube] abc: Video unavailable"));
        assertTrue(Baixador.traduzir("unable to download video data: HTTP Error 403: Forbidden")
                .contains("Atualizar"));
        // O desconhecido passa como veio.
        assertEquals("algo novo", Baixador.traduzir("algo novo"));
    }

    @Test
    @DisplayName("Tamanhos e tempos no jeito brasileiro")
    void formatos() {
        assertEquals("3,2 MB", PainelYoutube.mb(3_355_443));
        assertEquals("512 KB", PainelYoutube.mb(524_288));
        assertEquals("0:12", PainelYoutube.tempo(12));
        assertEquals("1:02:07", PainelYoutube.tempo(3727));
    }
}
