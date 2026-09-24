package br.com.myapp.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gerador de senhas.
 *
 * Usa {@link SecureRandom}, e não {@code Math.random()}: o gerador comum é
 * previsível o bastante para ser reconstruído por quem observe algumas
 * saídas, o que arruinaria justamente o que a senha deveria proteger.
 */
public final class GeradorSenha {

    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String MAIUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITOS = "23456789";
    private static final String SIMBOLOS = "!@#$%&*+-=?_";

    /** Caracteres fáceis de confundir, incluídos apenas quando permitido. */
    private static final String AMBIGUOS_MINUSCULAS = "jl";
    private static final String AMBIGUOS_MAIUSCULAS = "IO";
    private static final String AMBIGUOS_DIGITOS = "01";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeradorSenha() {
    }

    /** Opções do gerador, com valores que servem para a maioria dos casos. */
    public static class Opcoes {
        public int tamanho = 16;
        public boolean minusculas = true;
        public boolean maiusculas = true;
        public boolean digitos = true;
        public boolean simbolos = true;

        /**
         * Evitar caracteres parecidos entre si (l, 1, I, O, 0, j).
         *
         * Vale a pena quando a senha vai ser ditada por telefone ou digitada
         * a partir de um papel — situações comuns no atendimento a cliente.
         */
        public boolean evitarAmbiguos = true;
    }

    /**
     * Gera uma senha.
     *
     * Garante ao menos um caractere de cada grupo escolhido: sem isso, o acaso
     * pode produzir uma senha sem nenhum dígito, que alguns sistemas recusam.
     */
    public static String gerar(Opcoes opcoes) {
        StringBuilder alfabeto = new StringBuilder();
        List<Character> obrigatorios = new ArrayList<>();

        if (opcoes.minusculas) {
            String grupo = MINUSCULAS + (opcoes.evitarAmbiguos ? "" : AMBIGUOS_MINUSCULAS);
            alfabeto.append(grupo);
            obrigatorios.add(sortear(grupo));
        }
        if (opcoes.maiusculas) {
            String grupo = MAIUSCULAS + (opcoes.evitarAmbiguos ? "" : AMBIGUOS_MAIUSCULAS);
            alfabeto.append(grupo);
            obrigatorios.add(sortear(grupo));
        }
        if (opcoes.digitos) {
            String grupo = DIGITOS + (opcoes.evitarAmbiguos ? "" : AMBIGUOS_DIGITOS);
            alfabeto.append(grupo);
            obrigatorios.add(sortear(grupo));
        }
        if (opcoes.simbolos) {
            alfabeto.append(SIMBOLOS);
            obrigatorios.add(sortear(SIMBOLOS));
        }

        if (alfabeto.length() == 0) {
            // Nenhum grupo marcado: em vez de falhar, devolve algo utilizável.
            alfabeto.append(MINUSCULAS).append(DIGITOS);
        }

        int tamanho = Math.max(4, Math.min(opcoes.tamanho, 128));
        List<Character> senha = new ArrayList<>(obrigatorios.subList(
                0, Math.min(obrigatorios.size(), tamanho)));

        while (senha.size() < tamanho) {
            senha.add(alfabeto.charAt(ALEATORIO.nextInt(alfabeto.length())));
        }

        // Embaralha para os obrigatórios não ficarem sempre no começo.
        Collections.shuffle(senha, ALEATORIO);

        StringBuilder resultado = new StringBuilder(senha.size());
        for (char c : senha) {
            resultado.append(c);
        }
        return resultado.toString();
    }

    /** Senha com as opções padrão: 16 caracteres, tudo ligado, sem ambíguos. */
    public static String gerar() {
        return gerar(new Opcoes());
    }

    /**
     * Uma frase-senha, feita de palavras.
     *
     * Mais fácil de memorizar e de digitar que uma sequência aleatória, e
     * longa o bastante para compensar a menor variedade. Boa escolha quando a
     * senha precisa ser lembrada por uma pessoa.
     */
    public static String gerarFrase(int palavras, String separador) {
        String[] banco = {
                "aurora", "bosque", "cravo", "dunas", "eclipse", "farol", "garoa",
                "horizonte", "ilha", "jangada", "lapis", "marola", "nuvem", "oceano",
                "pedra", "quintal", "raiz", "serra", "trilha", "urso", "vento",
                "xarope", "zebra", "bambu", "cascata", "duna", "estrela", "fogueira",
                "girassol", "harpa", "iceberg", "janela", "lagoa", "montanha",
                "neblina", "orvalho", "praia", "quartzo", "rio", "sombra", "tucano",
                "vulcao", "abelha", "barco", "cedro", "deserto", "enseada", "floresta"
        };
        StringBuilder frase = new StringBuilder();
        int quantidade = Math.max(3, Math.min(palavras, 10));
        for (int i = 0; i < quantidade; i++) {
            if (i > 0) {
                frase.append(separador);
            }
            frase.append(banco[ALEATORIO.nextInt(banco.length)]);
        }
        // Um número no fim atende aos sistemas que exigem dígito.
        frase.append(separador).append(10 + ALEATORIO.nextInt(90));
        return frase.toString();
    }

    private static char sortear(String grupo) {
        return grupo.charAt(ALEATORIO.nextInt(grupo.length()));
    }
}
