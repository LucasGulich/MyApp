package br.com.myapp.modules.atalhos;

import br.com.myapp.core.Log;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.IconeDoWindows;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Os ícones dos atalhos: pegar o de verdade e desenhar o bloco.
 *
 * <p><b>Pegar</b> bloqueia e pode demorar (pasta de rede, site lento): chamar
 * fora da tela. Tudo tem limite de tempo e, sem ícone, o atalho fica com o
 * símbolo do tipo — nunca quebra.
 *
 * <ul>
 *   <li>Programa, pasta, arquivo: o mesmo ícone que o Explorador mostra
 *       ({@code FileSystemView.getSystemIcon}, em tamanho grande).</li>
 *   <li>Site: o favicon, pelo serviço de ícones do Google — só o domínio
 *       sai da máquina, nunca o endereço inteiro. Ver decisão 51.</li>
 * </ul>
 */
public final class IconesAtalho {

    /** Lado, em pixels, do PNG guardado. O bloco desenha em 40; 64 dá folga para escala do Windows. */
    static final int LADO = 64;

    private static final Duration LIMITE = Duration.ofSeconds(6);
    private static HttpClient http;

    private IconesAtalho() {
    }

    // ============================================================= pegar

    /** O ícone de verdade do alvo, em PNG, ou vazio se não deu. */
    public static Optional<byte[]> automatico(TipoAtalho tipo, String alvo) {
        if (alvo == null || alvo.isBlank()) {
            return Optional.empty();
        }
        try {
            return CompletableFuture
                    .supplyAsync(() -> tipo == TipoAtalho.SITE ? doSite(alvo) : doArquivo(alvo))
                    .orTimeout(LIMITE.toMillis(), TimeUnit.MILLISECONDS)
                    .get();
        } catch (Exception e) {
            Log.aviso("Sem ícone para " + alvo + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<byte[]> doArquivo(String alvo) {
        return IconeDoWindows.de(new File(alvo.trim().replace("\"", "")), LADO);
    }

    private static Optional<byte[]> doSite(String alvo) {
        try {
            String host = URI.create(AtalhoService.normalizarSite(alvo)).getHost();
            if (host == null) {
                return Optional.empty();
            }
            String url = "https://www.google.com/s2/favicons?sz=" + LADO + "&domain="
                    + URLEncoder.encode(host, StandardCharsets.UTF_8);
            HttpResponse<byte[]> r = cliente().send(HttpRequest.newBuilder(URI.create(url))
                            .header("User-Agent", "MyApp").timeout(LIMITE).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            // Domínio que o Google não conhece volta 404 com um globo genérico:
            // melhor o símbolo do MyApp, que segue o tema.
            if (r.statusCode() != 200) {
                return Optional.empty();
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
            return img == null ? Optional.empty() : png(ajustar(img));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Uma imagem escolhida (PNG, JPG, GIF, BMP), já no tamanho do bloco. */
    public static byte[] daImagem(Path arquivo) throws java.io.IOException {
        BufferedImage img = ImageIO.read(arquivo.toFile());
        if (img == null) {
            throw new java.io.IOException("Esse arquivo não é uma imagem que eu consiga ler. "
                    + "Use PNG, JPG, GIF ou BMP.");
        }
        return png(ajustar(img)).orElseThrow(() -> new java.io.IOException("Não consegui converter a imagem."));
    }

    /** Cabe a imagem num quadrado de {@link #LADO}, sem distorcer, centralizada. */
    static BufferedImage ajustar(BufferedImage origem) {
        double escala = Math.min((double) LADO / origem.getWidth(), (double) LADO / origem.getHeight());
        int w = Math.max(1, (int) Math.round(origem.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(origem.getHeight() * escala));
        BufferedImage destino = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(origem, (LADO - w) / 2, (LADO - h) / 2, w, h, null);
        g.dispose();
        return destino;
    }

    private static Optional<byte[]> png(BufferedImage img) {
        return IconeDoWindows.png(img);
    }

    private static synchronized HttpClient cliente() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(LIMITE)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return http;
    }

    // ============================================================ desenhar

    /**
     * O desenho do atalho num quadrado de {@code lado} pixels: a imagem
     * guardada, ou o símbolo branco num quadrado colorido.
     */
    public static Node desenho(Atalho a, double lado) {
        boolean temImagem = a.getModoIcone() != Atalho.ModoIcone.SIMBOLO && a.getIconePng() != null;
        if (temImagem) {
            Image img = new Image(new ByteArrayInputStream(a.getIconePng()), lado * 2, lado * 2, true, true);
            if (!img.isError()) {
                ImageView vista = new ImageView(img);
                vista.setFitWidth(lado);
                vista.setFitHeight(lado);
                vista.setPreserveRatio(true);
                vista.setSmooth(true);
                StackPane caixa = new StackPane(vista);
                caixa.setMinSize(lado, lado);
                caixa.setMaxSize(lado, lado);
                return caixa;
            }
        }
        Icone.Simbolo simbolo = a.getModoIcone() == Atalho.ModoIcone.SIMBOLO && a.getIconeSimbolo() != null
                ? Icone.porChave(a.getIconeSimbolo())
                : a.getTipo().simbolo;
        StackPane quadrado = new StackPane(Icone.de(simbolo, lado * 0.52));
        quadrado.getStyleClass().add("icone-atalho-simbolo");
        quadrado.setStyle("-fx-background-color: " + corSegura(a) + ";");
        quadrado.setMinSize(lado, lado);
        quadrado.setMaxSize(lado, lado);
        return quadrado;
    }

    /** A cor do quadrado: a escolhida, ou uma por tipo. */
    static String corSegura(Atalho a) {
        if (a.getCor() != null && !a.getCor().isBlank()) {
            try {
                Color.web(a.getCor());
                return a.getCor();
            } catch (Exception ignorado) {
                // cai na cor do tipo
            }
        }
        return switch (a.getTipo()) {
            case APLICATIVO -> "#4A8CFF";
            case SITE -> "#2BB5A0";
            case PASTA -> "#E0A030";
            case ARQUIVO -> "#8A7CF0";
        };
    }
}
