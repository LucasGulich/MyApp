package br.com.myapp.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Conversas com o Windows: iniciar junto com o sistema, tocar o som do alerta
 * e garantir uma única instância aberta.
 *
 * Tudo aqui e opcional e tolerante a falha: se a politica da máquina impedir
 * alguma dessas ações, o aplicativo continua funcionando normalmente.
 */
public final class WindowsIntegracao {

    private static final String CHAVE_RUN =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String NOME_ENTRADA = "MyApp";

    private WindowsIntegracao() {
    }

    // --------------------------------------------- iniciar com o Windows

    /** O aplicativo esta cadastrado para abrir junto com o Windows? */
    public static boolean iniciaComWindows() {
        try {
            Process p = new ProcessBuilder("reg", "query", CHAVE_RUN, "/v", NOME_ENTRADA)
                    .redirectErrorStream(true).start();
            boolean encontrou = p.waitFor() == 0;
            p.destroy();
            return encontrou;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Liga ou desliga a inicialização automática.
     *
     * Escreve apenas em HKCU (o ramo do usuário), que não exige privilegio de
     * administrador.
     */
    public static boolean definirIniciaComWindows(boolean ligar) {
        try {
            ProcessBuilder pb;
            if (ligar) {
                String comando = comandoDeInicializacao();
                if (comando == null) {
                    Log.aviso("Executando fora de um instalador: Inicialização automática indisponível.");
                    return false;
                }
                pb = new ProcessBuilder("reg", "add", CHAVE_RUN, "/v", NOME_ENTRADA,
                        "/t", "REG_SZ", "/d", comando, "/f");
            } else {
                pb = new ProcessBuilder("reg", "delete", CHAVE_RUN, "/v", NOME_ENTRADA, "/f");
            }
            Process p = pb.redirectErrorStream(true).start();
            boolean ok = p.waitFor() == 0;
            Log.info("Inicialização automática " + (ligar ? "ligada" : "desligada") + ": " + ok);
            return ok;
        } catch (Exception e) {
            Log.erro("Falha ao configurar a inicialização automática", e);
            return false;
        }
    }

    /**
     * Descobre o comando que reabre o aplicativo.
     *
     * Instalado pelo jpackage, existe um .exe - e o caminho ideal. Rodando
     * pelo Maven durante o desenvolvimento não ha executavel, e o método
     * devolve nulo em vez de cadastrar um comando que não funcionaria.
     */
    private static String comandoDeInicializacao() {
        String dirApp = System.getProperty("jpackage.app-path");
        if (dirApp != null && !dirApp.isBlank()) {
            return "\"" + dirApp + "\"";
        }
        // Segunda tentativa: o .exe gerado pelo jpackage fica ao lado da pasta app.
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path candidato = Paths.get(javaHome).getParent();
            if (candidato != null) {
                Path exe = candidato.resolve("MyApp.exe");
                if (Files.exists(exe)) {
                    return "\"" + exe.toAbsolutePath() + "\"";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------- som

    /**
     * Toca o som do alerta.
     *
     * Usa o .wav escolhido nas configurações; sem escolha, procura um som
     * padrão do Windows; sem nenhum dos dois, apenas apita.
     *
     * <p><b>Quem</b> toca é decisão de cada lembrete (campo som_ativo);
     * <b>qual</b> som toca é a preferência geral configurada aqui. Quem chama
     * este método já decidiu que o som deve sair.</p>
     */
    public static void tocarAlerta() {
        Config config = Config.get();
        new Thread(() -> {
            try {
                File som = escolherArquivoDeSom(config.somAlerta);
                if (som == null) {
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    return;
                }
                try (var entrada = javax.sound.sampled.AudioSystem.getAudioInputStream(som)) {
                    var clip = javax.sound.sampled.AudioSystem.getClip();
                    clip.open(entrada);
                    clip.start();
                    // Espera o som terminar antes de liberar o recurso.
                    Thread.sleep(Math.max(300, clip.getMicrosecondLength() / 1000));
                    clip.close();
                }
            } catch (Exception e) {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }, "som-alerta").start();
    }

    private static File escolherArquivoDeSom(String configurado) {
        if (configurado != null && !configurado.isBlank()) {
            File escolhido = new File(configurado);
            if (escolhido.isFile()) {
                return escolhido;
            }
        }
        for (String padrao : new String[]{
                "C:\\Windows\\Media\\Alarm01.wav",
                "C:\\Windows\\Media\\notify.wav",
                "C:\\Windows\\Media\\Windows Notify System Generic.wav"}) {
            File f = new File(padrao);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    // --------------------------------------------------- instância única

    private static java.nio.channels.FileLock trava;
    private static java.io.RandomAccessFile arquivoTrava;

    /**
     * Garante que só exista um MyApp aberto.
     *
     * Sem isso, duas instâncias disputariam o banco e você receberia cada
     * alerta duas vezes.
     *
     * @return true se esta instância conseguiu a trava
     */
    public static boolean garantirInstanciaUnica() {
        try {
            arquivoTrava = new java.io.RandomAccessFile(AppPaths.arquivoTrava().toFile(), "rw");
            trava = arquivoTrava.getChannel().tryLock();
            if (trava == null) {
                arquivoTrava.close();
                return false;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(WindowsIntegracao::liberarTrava));
            return true;
        } catch (Exception e) {
            Log.aviso("Não foi possível verificar instância única: " + e.getMessage());
            return true;   // na dúvida, deixa abrir
        }
    }

    private static void liberarTrava() {
        try {
            if (trava != null) {
                trava.release();
            }
            if (arquivoTrava != null) {
                arquivoTrava.close();
            }
        } catch (Exception ignorado) {
            // Encerrando de qualquer forma.
        }
    }
}
