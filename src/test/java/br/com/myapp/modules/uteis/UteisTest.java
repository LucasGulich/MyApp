package br.com.myapp.modules.uteis;

import br.com.myapp.modules.uteis.youtube.EventoDownload;
import br.com.myapp.modules.uteis.youtube.Ferramenta;
import br.com.myapp.modules.uteis.youtube.Midia;
import br.com.myapp.modules.uteis.youtube.PedidoDownload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Úteis: área de transferência e baixador do YouTube.
 *
 * <p>Nada aqui usa a rede nem o yt-dlp de verdade: as linhas e as listas de
 * soma são cópias do que os programas e o GitHub devolvem.
 */
class UteisTest {

    private static final Path YT = Path.of("C:/ferramentas/yt-dlp.exe");
    private static final Path PASTA = Path.of("C:/Downloads");

    // ------------------------------------------------ área de transferência

    @Test
    @DisplayName("Sobrescrever copia 25 textos todos diferentes — o Win+V não guarda repetidos")
    void sequenciaSemRepetidos() {
        List<String> s = PainelAreaTransferencia.sequencia(PainelAreaTransferencia.COPIAS);
        assertEquals(25, s.size());
        assertEquals(".", s.get(0));
        assertEquals("..", s.get(1));
        assertEquals("...", s.get(2));
        // Não basta a vizinha ser diferente: com "." e ".." alternados o
        // histórico ficava com dois itens só, e o resto continuava lá.
        assertEquals(s.size(), new java.util.HashSet<>(s).size(), "há textos repetidos");
    }

    @Test
    @DisplayName("Pausa entre as cópias longa o bastante para o Win+V registrar cada uma")
    void pausaEntreCopias() {
        // Com 150 ms o histórico perdia parte das cópias (relato do usuário).
        assertTrue(PainelAreaTransferencia.PAUSA.toMillis() >= 500,
                "pausa curta demais: " + PainelAreaTransferencia.PAUSA.toMillis() + " ms");
    }

    // ------------------------------------------------------ somas SHA-256

    @Test
    @DisplayName("Soma do yt-dlp: a linha do yt-dlp.exe, e não a do yt-dlp_x86.exe")
    void somaYtDlp() {
        String lista = """
                1111111111111111111111111111111111111111111111111111111111111111  yt-dlp
                66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a  yt-dlp.exe
                2222222222222222222222222222222222222222222222222222222222222222  yt-dlp_x86.exe
                """;
        assertEquals(Optional.of("66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a"),
                Ferramenta.YT_DLP.somaEsperada(lista));
    }

    @Test
    @DisplayName("Soma do FFmpeg: o win64-gpl, e não o win64-gpl-shared")
    void somaFfmpeg() {
        String lista = """
                20b2f500c9d94fc417ea770d37c4f27e4c26a707e54052973a0591cb663169c7  ffmpeg-master-latest-win64-gpl-shared.zip
                0111401c68487bd1966637266809b97feb98f87fb688a6f3197e27dfb1567eac  ffmpeg-master-latest-win64-gpl.zip
                """;
        assertEquals(Optional.of("0111401c68487bd1966637266809b97feb98f87fb688a6f3197e27dfb1567eac"),
                Ferramenta.FFMPEG.somaEsperada(lista));
    }

    @Test
    @DisplayName("Soma do Deno: formato do Get-FileHash, com o nome numa linha Path à parte")
    void somaDeno() {
        String lista = "\r\nAlgorithm : SHA256\r\n"
                + "Hash      : A0C3101B4158D1DFB7D6A78A7BF0F3DE80C96BB423C152BEEC8BEB22786F2238\r\n"
                + "Path      : C:\\a\\deno\\deno\\target\\release\\deno-x86_64-pc-windows-msvc.zip\r\n";
        assertEquals(Optional.of("a0c3101b4158d1dfb7d6a78a7bf0f3de80c96bb423c152beec8beb22786f2238"),
                Ferramenta.DENO.somaEsperada(lista));
    }

    @Test
    @DisplayName("Lista sem o arquivo e com várias somas: não arrisca nenhuma")
    void somaAmbigua() {
        String lista = """
                1111111111111111111111111111111111111111111111111111111111111111  outro.zip
                2222222222222222222222222222222222222222222222222222222222222222  mais-um.zip
                """;
        assertTrue(Ferramenta.FFMPEG.somaEsperada(lista).isEmpty());
    }

    // ------------------------------------------------------ linha de comando

    @Test
    @DisplayName("Vídeo em 720p: limita a altura, prefere H.264 e junta em MP4")
    void comandoVideo() {
        PedidoDownload p = new PedidoDownload(" https://youtu.be/abc ", PedidoDownload.Tipo.VIDEO, 720,
                PedidoDownload.FormatoAudio.MP3, "0", false, false, PASTA);
        List<String> c = p.comando(YT, Optional.empty(), Optional.empty());

        assertEquals(YT.toString(), c.get(0));
        assertEquals("bv*[height<=720]+ba/b[height<=720]", depois(c, "-f"));
        assertEquals("mp4", depois(c, "--merge-output-format"));
        assertTrue(c.contains("--no-playlist"));
        assertFalse(c.contains("-x"));
        assertFalse(c.contains("--embed-thumbnail"));
        assertFalse(c.contains("--ffmpeg-location"));
        assertEquals("--", c.get(c.size() - 2));
        assertEquals("https://youtu.be/abc", c.get(c.size() - 1));
    }

    @Test
    @DisplayName("Melhor vídeo: sem limite de altura")
    void comandoMelhorVideo() {
        PedidoDownload p = new PedidoDownload("https://youtu.be/abc", PedidoDownload.Tipo.VIDEO, 0,
                PedidoDownload.FormatoAudio.MP3, "0", false, true, PASTA);
        assertEquals("bv*+ba/b", depois(p.comando(YT, Optional.empty(), Optional.empty()), "-f"));
    }

    @Test
    @DisplayName("Áudio MP3 192 kbps, com capa, playlist e programas do MyApp")
    void comandoAudio() {
        Path ffmpeg = Path.of("C:/ferramentas");
        Path deno = Path.of("C:/ferramentas/deno.exe");
        PedidoDownload p = new PedidoDownload("https://youtu.be/abc", PedidoDownload.Tipo.AUDIO, 1080,
                PedidoDownload.FormatoAudio.MP3, "192K", true, true, PASTA);
        List<String> c = p.comando(YT, Optional.of(ffmpeg), Optional.of(deno));

        assertTrue(c.contains("-x"));
        assertEquals("mp3", depois(c, "--audio-format"));
        assertEquals("192K", depois(c, "--audio-quality"));
        assertFalse(c.contains("-f"), "áudio não escolhe formato de vídeo");
        assertTrue(c.contains("--embed-thumbnail"));
        assertTrue(c.contains("--yes-playlist"));
        assertTrue(depois(c, "-o").startsWith("%(playlist_title)s/"));
        assertEquals(ffmpeg.toString(), depois(c, "--ffmpeg-location"));
        assertEquals("deno:" + deno, depois(c, "--js-runtimes"));
        assertEquals(PASTA.toString(), depois(c, "-P"));
    }

    @Test
    @DisplayName("Só aceita link que começa com http(s)")
    void pareceLink() {
        assertTrue(PedidoDownload.pareceLink("https://www.youtube.com/watch?v=jNQXAC9IVRw"));
        assertTrue(PedidoDownload.pareceLink("  https://youtu.be/jNQXAC9IVRw  "));
        assertFalse(PedidoDownload.pareceLink("youtube.com/watch?v=x"));
        assertFalse(PedidoDownload.pareceLink("--exec calc"));
        assertFalse(PedidoDownload.pareceLink(""));
        assertFalse(PedidoDownload.pareceLink(null));
    }

    // ----------------------------------------------------------- progresso

    @Test
    @DisplayName("Linha de progresso: bytes, total, velocidade e tempo restante")
    void progresso() {
        EventoDownload e = EventoDownload.ler(
                "MYAPP|downloading|130048|223779|NA|16386602.775|3|NA|NA").orElseThrow();
        assertEquals(EventoDownload.Tipo.BAIXANDO, e.tipo());
        assertEquals(130048, e.baixado());
        assertEquals(223779, e.total());
        assertEquals(3, e.faltaSegundos());
        assertEquals(-1, e.item());
        assertEquals(130048.0 / 223779, e.fracao(), 1e-9);
    }

    @Test
    @DisplayName("Sem total exato, usa a estimativa; sem nenhum, a fração é desconhecida")
    void progressoEstimado() {
        EventoDownload estimado = EventoDownload.ler("MYAPP|downloading|500|NA|1000|NA|NA|2|12").orElseThrow();
        assertEquals(0.5, estimado.fracao(), 1e-9);
        assertEquals(2, estimado.item());
        assertEquals(12, estimado.itens());

        EventoDownload semNada = EventoDownload.ler("MYAPP|downloading|500|NA|NA|NA|NA|NA|NA").orElseThrow();
        assertEquals(-1, semNada.fracao());
    }

    @Test
    @DisplayName("Fim de parte, etapa, arquivo pronto e erro")
    void outrasLinhas() {
        assertEquals(EventoDownload.Tipo.PARTE_PRONTA,
                EventoDownload.ler("MYAPP|finished|223779|223779|NA|2201682.7|NA|NA|NA").orElseThrow().tipo());

        EventoDownload etapa = EventoDownload.ler("MYAPP-PP|started|Merger").orElseThrow();
        assertEquals(EventoDownload.Tipo.ETAPA, etapa.tipo());
        assertEquals("Juntando vídeo e áudio…", EventoDownload.descreverEtapa(etapa.texto()));
        assertTrue(EventoDownload.ler("MYAPP-PP|finished|Merger").isEmpty(), "só o começo da etapa conta");

        EventoDownload arquivo = EventoDownload.ler("MYAPP-ARQUIVO|C:\\Downloads\\Me at the zoo.mp4").orElseThrow();
        assertEquals("C:\\Downloads\\Me at the zoo.mp4", arquivo.texto());

        EventoDownload erro = EventoDownload.ler("ERROR: [youtube] abc: Video unavailable").orElseThrow();
        assertEquals(EventoDownload.Tipo.ERRO, erro.tipo());
        assertEquals("[youtube] abc: Video unavailable", erro.texto());

        assertTrue(EventoDownload.ler("[download] qualquer outra coisa").isEmpty());
    }

    // --------------------------------------------------------------- mídia

    @Test
    @DisplayName("Informações do vídeo: título, canal, duração e só as alturas de vídeo")
    void midiaVideo() throws Exception {
        Midia m = Midia.doJson("""
                {"title": "Me at the zoo", "channel": "jawed", "duration": 19,
                 "thumbnail": "https://i.ytimg.com/vi/x/hq.jpg",
                 "formats": [
                   {"format_id": "140", "vcodec": "none", "height": null},
                   {"format_id": "160", "vcodec": "avc1", "height": 144},
                   {"format_id": "134", "vcodec": "avc1", "height": 360},
                   {"format_id": "18",  "vcodec": "avc1", "height": 360},
                   {"format_id": "sb0", "vcodec": "none", "height": 90}
                 ]}
                """);
        assertEquals("Me at the zoo", m.titulo());
        assertEquals("jawed", m.canal());
        assertEquals("0:19", m.duracaoFormatada());
        assertEquals(List.of(360, 144), m.alturas());
        assertEquals(360, m.maiorAltura());
        assertFalse(m.playlist());
    }

    @Test
    @DisplayName("Informações de playlist: título e quantos vídeos")
    void midiaPlaylist() throws Exception {
        Midia m = Midia.doJson("""
                {"_type": "playlist", "title": "Aulas", "uploader": "Canal",
                 "entries": [{"id": "a"}, {"id": "b"}, {"id": "c"}]}
                """);
        assertTrue(m.playlist());
        assertEquals(3, m.itens());
        assertEquals("Canal", m.canal());
        assertEquals("", m.duracaoFormatada());
    }

    private static String depois(List<String> comando, String opcao) {
        int i = comando.indexOf(opcao);
        assertTrue(i >= 0 && i + 1 < comando.size(), "faltou " + opcao);
        return comando.get(i + 1);
    }
}
