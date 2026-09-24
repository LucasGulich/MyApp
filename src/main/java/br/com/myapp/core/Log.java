package br.com.myapp.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log simples em arquivo diario.
 *
 * Regra de ouro deste projeto: NUNCA passar conteúdo sensível para o log.
 * Registramos identificadores e ações, nunca o texto de um item protegido
 * nem, em hipotese alguma, a senha mestra.
 */
public final class Log {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DIAS_PARA_MANTER = 30;

    private Log() {
    }

    public static void info(String mensagem) {
        escrever("INFO ", mensagem, null);
    }

    public static void aviso(String mensagem) {
        escrever("AVISO", mensagem, null);
    }

    public static void erro(String mensagem, Throwable t) {
        escrever("ERRO ", mensagem, t);
    }

    private static synchronized void escrever(String nivel, String mensagem, Throwable t) {
        String linha = HORA.format(LocalDateTime.now()) + " [" + nivel + "] " + mensagem;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            linha = linha + System.lineSeparator() + sw;
        }
        System.out.println(linha);
        try {
            Path arquivo = AppPaths.pastaLogs().resolve("myapp-" + LocalDate.now() + ".log");
            Files.writeString(arquivo, linha + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Se nem o log funciona, não ha muito o que fazer além do console.
            System.err.println("Falha ao gravar log: " + e.getMessage());
        }
    }

    /** Remove logs antigos. Chamado uma vez na inicialização. */
    public static void limparAntigos() {
        try (var arquivos = Files.list(AppPaths.pastaLogs())) {
            LocalDate limite = LocalDate.now().minusDays(DIAS_PARA_MANTER);
            arquivos.filter(p -> p.getFileName().toString().startsWith("myapp-"))
                    .forEach(p -> {
                        try {
                            String nome = p.getFileName().toString();
                            LocalDate data = LocalDate.parse(nome.substring(6, 16));
                            if (data.isBefore(limite)) {
                                Files.deleteIfExists(p);
                            }
                        } catch (Exception ignorado) {
                            // Arquivo com nome fora do padrão: deixa quieto.
                        }
                    });
        } catch (IOException e) {
            aviso("Não foi possível limpar logs antigos: " + e.getMessage());
        }
    }
}
