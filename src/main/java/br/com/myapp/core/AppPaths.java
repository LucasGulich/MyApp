package br.com.myapp.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centraliza todos os caminhos usados pelo aplicativo.
 *
 * Por padrão tudo vive em %APPDATA%\MyApp - fora da Área de Trabalho e fora
 * de qualquer pasta sincronizada. Se a variável de ambiente não existir, cai
 * para a pasta do usuário.
 *
 * A propriedade de sistema {@code myapp.home} tem prioridade sobre tudo. Ela
 * atende dois casos: rodar em modo portátil (de um pendrive, apontando para
 * uma pasta ao lado do executável) e isolar cada teste automatizado em uma
 * pasta temporária própria.
 */
public final class AppPaths {

    /** Propriedade de sistema que redireciona a pasta de dados. */
    public static final String PROPRIEDADE_RAIZ = "myapp.home";

    private static final String NOME_PASTA = "MyApp";

    private static Path raiz;

    private AppPaths() {
    }

    /** Pasta raiz de dados: %APPDATA%\MyApp, ou o que {@code myapp.home} indicar. */
    public static synchronized Path raiz() {
        if (raiz == null) {
            String escolhida = System.getProperty(PROPRIEDADE_RAIZ);
            if (escolhida != null && !escolhida.isBlank()) {
                raiz = criar(Paths.get(escolhida));
                return raiz;
            }
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isBlank())
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"));
            raiz = criar(base.resolve(NOME_PASTA));
        }
        return raiz;
    }

    /**
     * Esquece a pasta já resolvida, para que a próxima chamada leia de novo a
     * configuração. Existe para os testes trocarem de pasta entre cenários.
     */
    public static synchronized void redefinir() {
        raiz = null;
    }

    /** Banco de dados SQLite. */
    public static Path bancoDeDados() {
        return raiz().resolve("myapp.db");
    }

    /** Preferências em JSON (nada sensível aqui). */
    public static Path arquivoConfig() {
        return raiz().resolve("config.json");
    }

    /** Arquivo de trava usado para impedir duas instâncias abertas. */
    public static Path arquivoTrava() {
        return raiz().resolve("app.lock");
    }

    /** Pasta de logs. */
    public static Path pastaLogs() {
        return criar(raiz().resolve("logs"));
    }

    /** Pasta de backups locais (a copia em nuvem e configurável a parte). */
    public static Path pastaBackups() {
        return criar(raiz().resolve("backups"));
    }

    /**
     * Programas de apoio baixados pelo MyApp (yt-dlp, FFmpeg, Deno).
     *
     * Fica em %LOCALAPPDATA%\MyApp\ferramentas, e não junto dos dados em
     * %APPDATA%: são uns 300 MB que se baixam de novo a qualquer hora, e o
     * %APPDATA% (Roaming) é a pasta que perfis corporativos copiam a cada
     * login. Com {@code myapp.home} definido — os testes —, vai para dentro
     * da raiz escolhida, para nada escapar da pasta temporária.
     */
    public static Path pastaFerramentas() {
        String escolhida = System.getProperty(PROPRIEDADE_RAIZ);
        String local = System.getenv("LOCALAPPDATA");
        if ((escolhida == null || escolhida.isBlank()) && local != null && !local.isBlank()) {
            return criar(Paths.get(local, NOME_PASTA, "ferramentas"));
        }
        return criar(raiz().resolve("ferramentas"));
    }

    private static Path criar(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta: " + p, e);
        }
    }
}
