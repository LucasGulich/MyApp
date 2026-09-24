package br.com.myapp.backup;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.security.CryptoService;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Backup do banco em arquivo único, cifrado e autossuficiente.
 *
 * Decisão importante: o backup não usa a chave que esta na memória. Ele deriva
 * uma chave própria a partir da senha informada no momento, e guarda o salt
 * dentro do próprio arquivo.
 *
 * A consequência prática e que o arquivo abre em qualquer máquina, só com a
 * senha - sem depender deste computador, deste banco ou desta instalação. Que
 * e exatamente o que se espera de um backup.
 *
 * Formato do arquivo .myappbkp:
 *
 *   linha 1: Cabeçalho JSON em texto (versão, salt, iteracoes, data)
 *   linha 2: Conteúdo do banco cifrado com AES-GCM, em Base64
 */
public final class BackupService {

    private static final String EXTENSAO = ".myappbkp";
    private static final String MARCA = "MYAPP-BACKUP-V1";
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private static ScheduledExecutorService agenda;

    private BackupService() {
    }

    // ---------------------------------------------------------- Exportação

    /**
     * Gera um backup cifrado.
     *
     * @param destino pasta onde gravar
     * @param senha   senha que protege o arquivo; limpa dentro do método
     * @return caminho do arquivo gerado
     */
    public static Path exportar(Path destino, char[] senha) throws IOException {
        try {
            Files.createDirectories(destino);
            Path arquivo = destino.resolve(
                    "myapp_" + CARIMBO.format(LocalDateTime.now()) + EXTENSAO);

            // Copia o banco antes de ler, para não capturar uma escrita pela metade.
            Path copia = Files.createTempFile("myapp-bkp", ".db");
            try {
                Files.copy(AppPaths.bancoDeDados(), copia, StandardCopyOption.REPLACE_EXISTING);
                byte[] conteudo = Files.readAllBytes(copia);

                byte[] salt = CryptoService.novoSalt();
                SecretKey chave = CryptoService.derivarChave(senha, salt, CryptoService.ITERACOES);
                byte[] cifrado = CryptoService.cifrar(conteudo, chave);
                CryptoService.limpar(conteudo);

                String cabecalho = "{\"marca\":\"" + MARCA + "\""
                        + ",\"salt\":\"" + Base64.getEncoder().encodeToString(salt) + "\""
                        + ",\"iteracoes\":" + CryptoService.ITERACOES
                        + ",\"gerado_em\":\"" + LocalDateTime.now() + "\"}";

                String corpo = cabecalho + System.lineSeparator()
                        + Base64.getEncoder().encodeToString(cifrado);

                Files.writeString(arquivo, corpo, StandardCharsets.UTF_8);
                Log.info("Backup gerado: " + arquivo.getFileName());
                return arquivo;
            } finally {
                Files.deleteIfExists(copia);
            }
        } finally {
            CryptoService.limpar(senha);
        }
    }

    /**
     * Restaura um backup por cima do banco atual.
     *
     * O banco vigente e preservado como .antes-da-restauracao, para não haver
     * caminho sem volta caso o arquivo escolhido não seja o esperado.
     *
     * @param senha limpa dentro do método
     */
    public static void restaurar(Path arquivo, char[] senha) throws IOException {
        try {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            if (linhas.size() < 2 || !linhas.get(0).contains(MARCA)) {
                throw new IOException("Este arquivo não parece ser um backup do MyApp.");
            }
            String cabecalho = linhas.get(0);
            byte[] salt = Base64.getDecoder().decode(extrairTexto(cabecalho, "salt"));
            int iteracoes = Integer.parseInt(extrairNumero(cabecalho, "iteracoes"));

            SecretKey chave = CryptoService.derivarChave(senha, salt, iteracoes);
            byte[] cifrado = Base64.getDecoder().decode(linhas.get(1));
            byte[] conteudo = CryptoService.decifrar(cifrado, chave);   // senha errada estoura aqui

            Path atual = AppPaths.bancoDeDados();
            if (Files.exists(atual)) {
                Files.copy(atual, atual.resolveSibling("myapp.db.antes-da-restauracao"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(atual, conteudo);
            CryptoService.limpar(conteudo);
            Log.info("Backup restaurado de " + arquivo.getFileName());
        } finally {
            CryptoService.limpar(senha);
        }
    }

    // --------------------------------------------------- backup automático

    /**
     * Liga o backup automático.
     *
     * Para rodar sem intervencao, o backup automático usa uma senha derivada
     * da própria máquina em vez de pedir a sua a cada execução. Ele protege
     * contra perda de arquivo e leitura casual na nuvem, não contra um ataque
     * dirigido: para levar o backup para fora, use a exportação manual com
     * senha própria.
     */
    public static synchronized void iniciarAutomatico() {
        if (agenda != null) {
            return;
        }
        agenda = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread t = new Thread(tarefa, "backup-automatico");
            t.setDaemon(true);
            return t;
        });
        // Verifica de hora em hora se já passou o intervalo configurado.
        agenda.scheduleWithFixedDelay(BackupService::rodarSeEstiverNaHora, 2, 60, TimeUnit.MINUTES);
        Log.info("Backup automático ativo.");
    }

    public static synchronized void pararAutomatico() {
        if (agenda != null) {
            agenda.shutdownNow();
            agenda = null;
        }
    }

    private static void rodarSeEstiverNaHora() {
        try {
            Config config = Config.get();
            if (!config.backupAutomatico) {
                return;
            }
            long intervaloMillis = Math.max(1, config.horasEntreBackups) * 3_600_000L;
            if (Instant.now().toEpochMilli() - config.ultimoBackupMillis < intervaloMillis) {
                return;
            }

            Path destino = destinoAutomatico(config);
            exportar(destino, senhaDaMaquina());
            limparAntigos(destino, config.backupsParaManter);

            config.ultimoBackupMillis = Instant.now().toEpochMilli();
            config.salvar();
        } catch (Exception e) {
            Log.erro("Falha no backup automático", e);
        }
    }

    /** Pasta configurada pelo usuário, ou a pasta local de backups. */
    private static Path destinoAutomatico(Config config) {
        if (config.pastaBackupNuvem != null && !config.pastaBackupNuvem.isBlank()) {
            Path escolhida = Paths.get(config.pastaBackupNuvem);
            if (Files.isDirectory(escolhida) || escolhida.toFile().mkdirs()) {
                return escolhida;
            }
            Log.aviso("Pasta de backup na nuvem indisponível; usando a pasta local.");
        }
        return AppPaths.pastaBackups();
    }

    /** Apaga os backups mais antigos, mantendo apenas os N mais recentes. */
    private static void limparAntigos(Path pasta, int manter) {
        try (var arquivos = Files.list(pasta)) {
            List<Path> lista = new ArrayList<>(arquivos
                    .filter(p -> p.getFileName().toString().endsWith(EXTENSAO))
                    .toList());
            lista.sort(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed());

            for (int i = Math.max(1, manter); i < lista.size(); i++) {
                Files.deleteIfExists(lista.get(i));
            }
        } catch (IOException e) {
            Log.aviso("Não foi possível limpar backups antigos: " + e.getMessage());
        }
    }

    /**
     * Senha usada pelo backup automático.
     *
     * Combina identificadores da máquina e do usuário. Não e segredo forte -
     * e por isso que o texto da tela avisa que o backup automático serve
     * contra perda, e não contra um atacante determinado.
     */
    private static char[] senhaDaMaquina() {
        String base = "myapp-auto-"
                + System.getProperty("user.name", "?") + "-"
                + System.getenv().getOrDefault("COMPUTERNAME", "?") + "-"
                + AppPaths.raiz().toAbsolutePath();
        return base.toCharArray();
    }

    /** Lista os backups existentes na pasta de destino. */
    public static List<Path> listar() {
        Path pasta = destinoAutomatico(Config.get());
        try (var arquivos = Files.list(pasta)) {
            return arquivos.filter(p -> p.getFileName().toString().endsWith(EXTENSAO))
                    .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------ apoio

    private static String extrairTexto(String json, String campo) throws IOException {
        String marca = "\"" + campo + "\":\"";
        int inicio = json.indexOf(marca);
        if (inicio < 0) {
            throw new IOException("Backup sem o campo " + campo + ".");
        }
        inicio += marca.length();
        int fim = json.indexOf('"', inicio);
        return json.substring(inicio, fim);
    }

    private static String extrairNumero(String json, String campo) throws IOException {
        String marca = "\"" + campo + "\":";
        int inicio = json.indexOf(marca);
        if (inicio < 0) {
            throw new IOException("Backup sem o campo " + campo + ".");
        }
        inicio += marca.length();
        int fim = inicio;
        while (fim < json.length() && Character.isDigit(json.charAt(fim))) {
            fim++;
        }
        return json.substring(inicio, fim);
    }
}
