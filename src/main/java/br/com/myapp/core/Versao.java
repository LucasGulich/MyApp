package br.com.myapp.core;

import java.io.InputStream;
import java.util.Properties;

/**
 * A versão do aplicativo, para mostrar na tela.
 *
 * O número mora num lugar só, o {@code <version>} do pom.xml. No build, o
 * Maven o copia para {@code versao.properties} — o único recurso filtrado —,
 * e é de lá que ele é lido. Assim a versão na tela nunca fica para trás da
 * que foi empacotada.
 */
public final class Versao {

    private static String atual;

    private Versao() {
    }

    /** "1.11.0", ou "dev" se o arquivo não foi preenchido pelo build. */
    public static synchronized String atual() {
        if (atual == null) {
            atual = ler();
        }
        return atual;
    }

    private static String ler() {
        try (InputStream entrada = Versao.class.getResourceAsStream("/versao.properties")) {
            if (entrada == null) {
                return "dev";
            }
            Properties p = new Properties();
            p.load(entrada);
            String v = p.getProperty("versao", "").trim();
            // Sem o filtro do Maven (rodando de uma IDE que não o aplica), o
            // arquivo traz o marcador cru em vez do número.
            return v.isEmpty() || v.startsWith("${") ? "dev" : v;
        } catch (Exception e) {
            return "dev";
        }
    }
}
