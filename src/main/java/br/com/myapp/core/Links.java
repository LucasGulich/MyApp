package br.com.myapp.core;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O único lugar que abre um endereço da web no MyApp.
 *
 * <p>Quem decide o navegador, do mais forte para o mais fraco:
 *
 * <ol>
 *   <li>o escolhido no atalho de site;</li>
 *   <li>a preferência do MyApp, em Configurações → Sistema
 *       ({@link Config#navegadorPadrao});</li>
 *   <li>o navegador padrão do Windows.</li>
 * </ol>
 *
 * <p>Vale para todo link: "Entrar" na reunião, link e endereço das notas, ação
 * de lembrete, alerta e atalhos. Só {@code http} e {@code https} passam pela
 * escolha; {@code mailto:} e outros vão para o programa que o Windows associa.
 *
 * <p>Bloqueia um instante (subir o processo): chamar fora da thread da tela.
 */
public final class Links {

    /** Sala do Meet, Zoom ou Teams. Usado pelo evento do Google e pelo lembrete. */
    private static final Pattern LINK_REUNIAO = Pattern.compile(
            "https://(?:meet\\.google\\.com|[\\w.-]*zoom\\.us|teams\\.microsoft\\.com|teams\\.live\\.com)"
                    + "/[^\\s<>\"']+");

    private Links() {
    }

    /**
     * O primeiro link de reunião (Meet, Zoom, Teams) no meio de um texto,
     * sem a pontuação que costuma grudar no fim — "(link)." vira "link".
     */
    public static Optional<String> linkDeReuniao(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Matcher m = LINK_REUNIAO.matcher(texto);
        return m.find() ? Optional.of(m.group().replaceAll("[).,;]+$", "")) : Optional.empty();
    }

    /** O endereço é de uma sala de reunião? */
    public static boolean ehReuniao(String endereco) {
        return endereco != null && LINK_REUNIAO.matcher(endereco.trim()).lookingAt();
    }

    /** Abre pela preferência do MyApp, ou pelo Windows. */
    public static void abrir(String endereco) throws IOException {
        abrir(endereco, null);
    }

    /**
     * @param doAtalho o navegador escolhido no atalho, ou nulo/vazio
     * @throws IOException endereço inválido, ou o navegador do atalho não existe
     *                     mais — a mensagem é para a tela
     */
    public static void abrir(String endereco, String doAtalho) throws IOException {
        URI uri;
        try {
            uri = URI.create(endereco.trim());
        } catch (Exception e) {
            throw new IOException("O endereço \"" + endereco + "\" não é um link válido.");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("http") && !esquema.equals("https")) {
            Desktop.getDesktop().browse(uri);
            return;
        }

        // O do atalho é escolha explícita para aquele site: se sumiu, avisar
        // é melhor que abrir em outro sem dizer nada.
        if (doAtalho != null && !doAtalho.isBlank() && !existe(doAtalho)) {
            throw new IOException("O navegador escolhido para este atalho não está mais instalado ("
                    + new File(doAtalho).getName() + "). Edite o atalho e escolha outro.");
        }

        String geral = Config.get().navegadorPadrao;
        if (geral != null && !geral.isBlank() && !existe(geral)) {
            // A preferência geral é pano de fundo: sem ela, o Windows resolve.
            Log.aviso("O navegador preferido do MyApp não existe mais (" + geral + "); usando o do Windows.");
        }

        Optional<String> navegador = escolher(doAtalho, geral, Links::existe);
        if (navegador.isPresent()) {
            // O endereço vai como argumento à parte: o Java cuida das aspas,
            // e um "&" no link não vira comando.
            new ProcessBuilder(navegador.get(), uri.toString()).start();
        } else {
            Desktop.getDesktop().browse(uri);
        }
    }

    /**
     * Qual navegador usar: o do atalho, se houver; senão a preferência geral,
     * se ela ainda existir; senão nenhum (o Windows decide).
     */
    static Optional<String> escolher(String doAtalho, String geral, Predicate<String> existe) {
        if (doAtalho != null && !doAtalho.isBlank()) {
            return Optional.of(doAtalho);
        }
        if (geral != null && !geral.isBlank() && existe.test(geral)) {
            return Optional.of(geral);
        }
        return Optional.empty();
    }

    private static boolean existe(String exe) {
        return new File(exe).isFile();
    }
}
