package br.com.myapp.ui;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Optional;

/**
 * O ícone que o Explorador mostra para um programa, pasta ou arquivo, em PNG.
 *
 * <p>Usado pelos atalhos e pela lista de navegadores. Bloqueia — numa pasta
 * de rede, pode demorar —: chamar fora da thread da tela.
 */
public final class IconeDoWindows {

    private IconeDoWindows() {
    }

    /** PNG de {@code lado} × {@code lado}, ou vazio se o arquivo não existe ou não tem ícone. */
    public static Optional<byte[]> de(File arquivo, int lado) {
        if (arquivo == null || !arquivo.exists()) {
            return Optional.empty();
        }
        Icon icone = FileSystemView.getFileSystemView().getSystemIcon(arquivo, lado, lado);
        if (icone == null) {
            return Optional.empty();
        }
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (icone instanceof ImageIcon imagem) {
            // A imagem do Windows vem em várias resoluções; pedir o tamanho
            // final escolhe a melhor, em vez de esticar a de 32.
            g.drawImage(imagem.getImage(), 0, 0, lado, lado, null);
        } else {
            int x = (lado - icone.getIconWidth()) / 2;
            int y = (lado - icone.getIconHeight()) / 2;
            icone.paintIcon(null, g, Math.max(0, x), Math.max(0, y));
        }
        g.dispose();
        return png(img);
    }

    public static Optional<byte[]> png(BufferedImage img) {
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(img, "png", saida);
            return Optional.of(saida.toByteArray());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
