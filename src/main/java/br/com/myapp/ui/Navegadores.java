package br.com.myapp.ui;

import br.com.myapp.core.Log;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Os navegadores instalados, para o site abrir no escolhido.
 *
 * <p>Vêm de onde o próprio Windows tira a lista de "aplicativo padrão para a
 * web": {@code SOFTWARE\Clients\StartMenuInternet}, na máquina (HKLM, também a
 * parte de 32 bits) e no usuário (HKCU — onde ficam Opera, Firefox e outros
 * instalados só para a pessoa). Cada navegador tem ali o nome e o comando de
 * abrir.
 *
 * <p>O Internet Explorer fica de fora: no Windows 11 ele só redireciona para o
 * Edge.
 */
public final class Navegadores {

    /** Um navegador instalado. {@code exe} vazio é o padrão do Windows. */
    public record Navegador(String nome, String exe, byte[] icone) {

        public boolean ehPadrao() {
            return exe == null || exe.isBlank();
        }
    }

    public static final Navegador PADRAO = new Navegador("Navegador padrão do Windows", "", null);

    private static final String[] RAIZES = {
            "HKLM\\SOFTWARE\\Clients\\StartMenuInternet",
            "HKCU\\SOFTWARE\\Clients\\StartMenuInternet",
            "HKLM\\SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet"
    };

    private static volatile List<Navegador> emCache;

    private Navegadores() {
    }

    /**
     * Os instalados, com ícone, sem o padrão. Lê o registro na primeira vez
     * (uns 300 ms) e guarda: chamar fora da tela, ou depois de {@link #carregarEmSegundoPlano(Runnable)}.
     */
    public static List<Navegador> instalados() {
        List<Navegador> lista = emCache;
        if (lista == null) {
            lista = ler();
            emCache = lista;
        }
        return lista;
    }

    /**
     * Começa a ler já, para o cadastro abrir sem espera e os blocos ganharem
     * o selo do navegador. {@code aoTerminar} roda na thread da leitura.
     */
    public static void carregarEmSegundoPlano(Runnable aoTerminar) {
        if (emCache != null) {
            return;
        }
        Thread t = new Thread(() -> {
            instalados();
            aoTerminar.run();
        }, "ler-navegadores");
        t.setDaemon(true);
        t.start();
    }

    /** O navegador do caminho, só se a lista já foi lida — nunca lê o registro. */
    public static java.util.Optional<Navegador> jaLido(String exe) {
        List<Navegador> lista = emCache;
        if (lista == null || exe == null || exe.isBlank()) {
            return java.util.Optional.empty();
        }
        return lista.stream().filter(n -> n.exe().equalsIgnoreCase(exe)).findFirst();
    }

    /** O nome para mostrar de um caminho gravado: "Google Chrome", ou o arquivo. */
    public static String nomeDe(String exe) {
        if (exe == null || exe.isBlank()) {
            return PADRAO.nome();
        }
        for (Navegador n : instalados()) {
            if (n.exe().equalsIgnoreCase(exe)) {
                return n.nome();
            }
        }
        return new File(exe).getName();
    }

    private static List<Navegador> ler() {
        StringBuilder tudo = new StringBuilder();
        for (String raiz : RAIZES) {
            try {
                Process p = new ProcessBuilder("reg", "query", raiz, "/s").redirectErrorStream(true).start();
                byte[] saida;
                try (InputStream in = p.getInputStream()) {
                    saida = in.readAllBytes();
                }
                p.waitFor(10, TimeUnit.SECONDS);
                // O reg.exe escreve na página de código do console (850 no
                // Windows em português).
                tudo.append(new String(saida, Charset.forName("IBM850"))).append('\n');
            } catch (Exception e) {
                Log.aviso("Não li os navegadores de " + raiz + ": " + e.getMessage());
            }
        }
        List<Navegador> lidos = new ArrayList<>();
        for (Navegador n : doRegistro(tudo.toString())) {
            if (new File(n.exe()).isFile()) {
                lidos.add(new Navegador(n.nome(), n.exe(),
                        IconeDoWindows.de(new File(n.exe()), 64).orElse(null)));
            }
        }
        lidos.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return List.copyOf(lidos);
    }

    /**
     * Lê a saída do {@code reg query ... /s}. O valor padrão de cada chave
     * aparece como "(Default)" ou "(padrão)", conforme o idioma do Windows:
     * vale qualquer nome entre parênteses.
     *
     * <pre>
     * HKEY_LOCAL_MACHINE\...\StartMenuInternet\Google Chrome
     *     (padrão)    REG_SZ    Google Chrome
     * HKEY_LOCAL_MACHINE\...\StartMenuInternet\Google Chrome\shell\open\command
     *     (padrão)    REG_SZ    "C:\Program Files\Google\Chrome\Application\chrome.exe"
     * </pre>
     */
    static List<Navegador> doRegistro(String saida) {
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, String> exes = new LinkedHashMap<>();
        String chave = null;
        for (String linha : saida.split("\\R")) {
            if (linha.startsWith("HKEY_")) {
                chave = linha.trim();
                continue;
            }
            if (chave == null || linha.isBlank()) {
                continue;
            }
            String[] partes = linha.trim().split("\\s{4,}", 3);
            if (partes.length < 3 || !partes[0].startsWith("(") || !partes[0].endsWith(")")) {
                continue;
            }
            int marca = chave.toLowerCase(Locale.ROOT).indexOf("\\startmenuinternet\\");
            if (marca < 0) {
                continue;
            }
            String resto = chave.substring(marca + "\\startmenuinternet\\".length());
            String id = resto.contains("\\") ? resto.substring(0, resto.indexOf('\\')) : resto;
            if (!resto.contains("\\")) {
                nomes.putIfAbsent(id, partes[2].trim());
            } else if (resto.toLowerCase(Locale.ROOT).endsWith("\\shell\\open\\command")) {
                exes.putIfAbsent(id, executavel(partes[2]));
            }
        }

        List<Navegador> lista = new ArrayList<>();
        List<String> vistos = new ArrayList<>();
        exes.forEach((id, exe) -> {
            String chaveExe = exe.toLowerCase(Locale.ROOT);
            if (exe.isBlank() || chaveExe.endsWith("iexplore.exe") || vistos.contains(chaveExe)) {
                return;
            }
            vistos.add(chaveExe);
            lista.add(new Navegador(nomes.getOrDefault(id, id), exe, null));
        });
        return lista;
    }

    /** {@code "C:\...\chrome.exe" --algo} → {@code C:\...\chrome.exe}. */
    static String executavel(String comando) {
        String c = comando.trim();
        if (c.startsWith("\"")) {
            int fim = c.indexOf('"', 1);
            return fim > 0 ? c.substring(1, fim) : c.substring(1);
        }
        int exe = c.toLowerCase(Locale.ROOT).indexOf(".exe");
        return exe > 0 ? c.substring(0, exe + 4) : c;
    }
}
