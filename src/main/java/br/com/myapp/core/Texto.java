package br.com.myapp.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Comparação de texto do jeito que uma pessoa compara.
 *
 * <p>Quem procura "agua" quer achar "Água", e quem digita "acao" quer achar
 * "ação". Comparar os caracteres crus não acha nenhum dos dois: para o
 * computador, "a" e "á" são tão diferentes quanto "a" e "z".
 *
 * <p>A saída é decompor cada letra acentuada na letra-base mais o acento
 * (forma NFD do Unicode: "á" vira "a" + "´") e jogar fora os acentos. O "ç"
 * entra na mesma regra — vira "c" mais a cedilha —, então "acucar" também
 * acha "açúcar".
 *
 * <p>Toda busca do aplicativo passa por aqui. Assim um módulo novo já nasce
 * procurando do mesmo jeito que os outros, sem cada tela reinventar a regra.
 */
public final class Texto {

    /** Os acentos soltos que sobram depois da decomposição. */
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");

    private Texto() {
    }

    /**
     * O texto na forma usada para comparar: sem acento e em minúsculas.
     *
     * <p>Nunca devolve nulo, para quem chama não precisar se proteger.
     */
    public static String paraBusca(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return ACENTOS.matcher(decomposto).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * O texto contém o termo, ignorando acentos e maiúsculas?
     *
     * <p>Termo vazio ou só com espaços casa com qualquer texto: é o campo de
     * busca em branco, que mostra tudo.
     */
    public static boolean contem(String texto, String termo) {
        String alvo = paraBusca(termo).trim();
        if (alvo.isEmpty()) {
            return true;
        }
        return paraBusca(texto).contains(alvo);
    }
}
