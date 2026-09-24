package br.com.myapp.ui;

import br.com.myapp.core.Log;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * O ícone do MyApp, desenhado em código.
 *
 * Desenhar em vez de carregar um .png tem três vantagens práticas: não há
 * arquivo para se perder no empacotamento, cada tamanho é gerado na resolução
 * exata que o Windows pede (nada de ícone borrado na barra de tarefas), e
 * mudar a identidade visual é mexer em um arquivo só.
 *
 * O desenho: um quadrado arredondado com gradiente azul-violeta e um raio
 * branco no centro. A forma foi escolhida para continuar legível em 16
 * pixels, onde a maior parte dos detalhes desaparece.
 *
 * <p><b>Por que um raio, e não um sino.</b> O sino veio da primeira versão,
 * quando o aplicativo era só lembretes. Hoje ele tem início, quadro, notas e
 * cofre — e um sino promete uma coisa só, a menos importante das quatro.
 * O raio é a mesma marca que aparece na barra lateral, então o ícone da
 * janela, o da barra de tarefas e o do menu passaram a ser o mesmo símbolo,
 * em vez de três identidades diferentes para o mesmo programa.
 */
public final class IconeApp {

    /** Tamanhos que o Windows costuma pedir, da barra de tarefas ao Alt+Tab. */
    private static final int[] TAMANHOS = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    // Paleta, alinhada com o tema do aplicativo.
    private static final Color AZUL = new Color(0x4C, 0x8D, 0xFF);
    private static final Color VIOLETA = new Color(0x7B, 0x5C, 0xFF);
    private static final Color BRANCO = new Color(0xFF, 0xFF, 0xFF);

    private IconeApp() {
    }

    // ------------------------------------------------------------- JavaFX

    /** Ícones da janela, em todos os tamanhos. Usado em {@code Stage.getIcons()}. */
    public static List<javafx.scene.image.Image> paraJanela() {
        List<javafx.scene.image.Image> imagens = new ArrayList<>();
        for (int tamanho : TAMANHOS) {
            try {
                byte[] png = paraPng(desenhar(tamanho));
                imagens.add(new javafx.scene.image.Image(new ByteArrayInputStream(png)));
            } catch (Exception e) {
                Log.aviso("Não foi possível gerar o ícone de " + tamanho + "px: " + e.getMessage());
            }
        }
        return imagens;
    }

    // --------------------------------------------------------------- AWT

    /** Ícone para a bandeja do sistema, que trabalha com imagens do AWT. */
    public static BufferedImage paraBandeja(int tamanho) {
        return desenhar(tamanho);
    }

    // ------------------------------------------------------------ desenho

    /**
     * Desenha o ícone no tamanho pedido.
     *
     * Todas as medidas são proporcionais ao lado, para o resultado ficar
     * equilibrado tanto em 16 quanto em 256 pixels.
     */
    public static BufferedImage desenhar(int lado) {
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double d = lado;

        // ---------- fundo: quadrado arredondado com gradiente na diagonal ----------
        double margem = d * 0.04;
        double tamanhoFundo = d - margem * 2;
        double arredondamento = d * 0.26;

        g.setPaint(new GradientPaint(
                (float) margem, (float) margem, AZUL,
                (float) (d - margem), (float) (d - margem), VIOLETA));
        g.fill(new RoundRectangle2D.Double(margem, margem, tamanhoFundo, tamanhoFundo,
                arredondamento, arredondamento));

        // Brilho de cima para baixo, dando leve volume ao fundo.
        // Precisa ser um degradê, e não um retângulo claro: o retângulo deixa
        // uma linha horizontal visível onde termina.
        if (lado >= 32) {
            java.awt.Shape recorte = g.getClip();
            g.clip(new RoundRectangle2D.Double(margem, margem, tamanhoFundo, tamanhoFundo,
                    arredondamento, arredondamento));
            g.setPaint(new GradientPaint(
                    0f, (float) margem, new Color(255, 255, 255, 52),
                    0f, (float) (d * 0.62), new Color(255, 255, 255, 0)));
            g.fillRect(0, 0, lado, lado);
            g.setClip(recorte);
        }

        // ---------- o raio ----------
        desenharRaio(g, d);

        g.dispose();
        return img;
    }

    /**
     * O raio, centralizado no quadrado.
     *
     * <p>É o mesmo traçado de {@code Icone.Simbolo.MARCA}, que aparece na
     * barra lateral — desenhado aqui em coordenadas do AWT porque o ícone da
     * janela é gerado fora do JavaFX, para o Windows.
     *
     * <p>Uma silhueta maciça, sem detalhe interno, é o que sobrevive à redução
     * para 16 pixels: qualquer linha fina vira borrão nesse tamanho.
     */
    private static void desenharRaio(Graphics2D g, double d) {
        // O desenho original vive numa grade de 24; estes são os mesmos
        // pontos, reduzidos para dois terços do quadrado e centralizados.
        double escala = d * 0.66 / 24.0;
        double desloca = d * 0.5 - 12 * escala;

        double[][] pontos = {
                {13, 2}, {3, 14}, {12, 14}, {11, 22}, {21, 10}, {12, 10}
        };

        GeneralPath raio = new GeneralPath();
        for (int i = 0; i < pontos.length; i++) {
            double x = desloca + pontos[i][0] * escala;
            double y = desloca + pontos[i][1] * escala;
            if (i == 0) {
                raio.moveTo(x, y);
            } else {
                raio.lineTo(x, y);
            }
        }
        raio.closePath();

        g.setPaint(BRANCO);
        g.fill(raio);
    }

    // --------------------------------------------------------------- apoio

    private static byte[] paraPng(BufferedImage img) throws java.io.IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", saida);
        return saida.toByteArray();
    }

    /**
     * Grava os PNGs em uma pasta, um por tamanho.
     *
     * Serve para gerar o .ico do instalador e para conferir o desenho sem
     * precisar abrir o aplicativo.
     */
    public static void exportar(java.nio.file.Path pasta) throws java.io.IOException {
        java.nio.file.Files.createDirectories(pasta);
        for (int tamanho : TAMANHOS) {
            java.nio.file.Files.write(
                    pasta.resolve("myapp-" + tamanho + ".png"),
                    paraPng(desenhar(tamanho)));
        }
        Log.info("Ícones exportados para " + pasta);
    }
}
