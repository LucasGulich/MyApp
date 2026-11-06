package br.com.myapp.modules.uteis.sql;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.core.FormatConfig;
import com.github.vertical_blank.sqlformatter.languages.Dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatar, compactar e preencher os {@code ?} de uma consulta SQL.
 *
 * <p>A quebra de linhas e o recuo são da biblioteca sql-formatter (decisão 54).
 * O resto é daqui, porque ela não faz ou faz diferente do que se quer:
 *
 * <ul>
 *   <li><b>Separar o log</b>: colar o trecho do log do Hibernate inteiro — a
 *       consulta e as linhas "binding parameter" — já basta.</li>
 *   <li><b>Preencher os {@code ?}</b> (e {@code $1}, {@code $2}), com aspas
 *       onde precisa, sem tocar no que está entre aspas ou em comentário.</li>
 *   <li><b>Caixa das palavras-chave</b>: maiúsculas, minúsculas ou como
 *       vieram — a biblioteca só sabe pôr em maiúsculas. Nomes de tabela e
 *       coluna nunca mudam.</li>
 *   <li>{@code id::text} continua junto (ela separava em {@code id :: TEXT}).</li>
 *   <li><b>Compactar</b> numa linha, trocando {@code -- comentário} por
 *       {@code /* comentário *\/} para ele não engolir o resto.</li>
 * </ul>
 */
public final class FormatadorSql {

    /** O banco: muda o que a biblioteca entende como palavra-chave e operador. */
    public enum Banco {
        POSTGRESQL("PostgreSQL", Dialect.PostgreSql),
        MYSQL("MySQL / MariaDB", Dialect.MySql),
        ORACLE("Oracle", Dialect.PlSql),
        SQL_SERVER("SQL Server", Dialect.TSql),
        PADRAO("SQL padrão", Dialect.StandardSql);

        public final String rotulo;
        final Dialect dialeto;

        Banco(String rotulo, Dialect dialeto) {
            this.rotulo = rotulo;
            this.dialeto = dialeto;
        }
    }

    /** Como ficam as palavras-chave (SELECT, FROM...). */
    public enum Caixa { MAIUSCULAS, MINUSCULAS, COMO_VEIO }

    /**
     * @param preenchidos     quantos {@code ?} receberam valor
     * @param semValor        quantos {@code ?} ficaram sem valor
     * @param valoresSobrando valores que não tinham {@code ?} para ir
     */
    public record Resultado(String sql, int preenchidos, int semValor, int valoresSobrando) {
    }

    private static final Pattern BINDING_5 =
            Pattern.compile("binding parameter \\[(\\d+)]\\s+as\\s+\\[(\\w+)]\\s+-\\s+\\[(.*)]\\s*$");
    private static final Pattern BINDING_6 =
            Pattern.compile("binding parameter \\((\\d+):(\\w+)\\)\\s+<-\\s+\\[(.*)]\\s*$");
    private static final Pattern LINHA_SQL_DO_LOG =
            Pattern.compile("^(?:.*?(?:org\\.hibernate\\.SQL|o\\.h\\.SQL)\\s*:\\s*|\\s*Hibernate:\\s*)(.*)$");
    private static final Pattern NUMERO = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Set<String> TIPOS_SEM_ASPAS = Set.of(
            "BIGINT", "INTEGER", "INT", "SMALLINT", "TINYINT", "NUMERIC", "DECIMAL", "DOUBLE",
            "FLOAT", "REAL", "BOOLEAN", "BIT", "NUMBER", "LONG", "SHORT");

    private FormatadorSql() {
    }

    // ================================================================ API

    public static Resultado formatar(String entrada, String valores, Banco banco, Caixa caixa, String recuo) {
        Separado log = separarLog(entrada);
        List<String> lista = valores != null && !valores.isBlank() ? lerValores(valores) : log.valores();
        Preenchido p = preencher(log.sql(), lista);

        String formatado = formatarComCaixa(p.sql(), banco, caixa, recuo);
        return new Resultado(juntarCast(formatado), p.preenchidos(), p.semValor(), p.sobrando());
    }

    /** Numa linha só: para colar em código, chamado ou script. */
    public static Resultado compactar(String entrada, String valores, Banco banco, Caixa caixa) {
        Resultado formatado = formatar(entrada, valores, banco, caixa, "  ");
        return new Resultado(compactarEspacos(formatado.sql()), formatado.preenchidos(),
                formatado.semValor(), formatado.valoresSobrando());
    }

    // ===================================================== separar o log

    record Separado(String sql, List<String> valores) {
    }

    /**
     * Tira do texto colado as linhas "binding parameter" (viram os valores)
     * e o prefixo do log antes da consulta ("Hibernate: ", "... org.hibernate.SQL : ").
     */
    static Separado separarLog(String entrada) {
        if (entrada == null) {
            return new Separado("", List.of());
        }
        TreeMap<Integer, String> porPosicao = new TreeMap<>();
        StringBuilder sql = new StringBuilder();
        for (String linha : entrada.split("\\R")) {
            Matcher m = BINDING_5.matcher(linha);
            if (!m.find()) {
                m = BINDING_6.matcher(linha);
                if (!m.find()) {
                    m = null;
                }
            }
            if (m != null) {
                porPosicao.put(Integer.parseInt(m.group(1)), comAspas(m.group(3), m.group(2)));
                continue;
            }
            Matcher doLog = LINHA_SQL_DO_LOG.matcher(linha);
            sql.append(doLog.matches() ? doLog.group(1) : linha).append('\n');
        }
        return new Separado(sql.toString().strip(), new ArrayList<>(porPosicao.values()));
    }

    /**
     * Os valores digitados à mão: um por linha, ou separados por vírgula numa
     * linha só. Número, true/false e null vão como estão; o resto ganha aspas.
     */
    static List<String> lerValores(String texto) {
        List<String> partes = new ArrayList<>();
        String t = texto.strip();
        if (t.isEmpty()) {
            return partes;
        }
        List<String> brutos = t.contains("\n") ? List.of(t.split("\\R")) : partirPorVirgula(t);
        for (String bruto : brutos) {
            String v = bruto.strip();
            if (v.isEmpty()) {
                continue;
            }
            // "[1532]", como aparece no log, vira 1532.
            if (v.startsWith("[") && v.endsWith("]") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            partes.add(comAspas(v, null));
        }
        return partes;
    }

    private static List<String> partirPorVirgula(String linha) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emAspas = false;
        for (char c : linha.toCharArray()) {
            if (c == '\'') {
                emAspas = !emAspas;
            }
            if (c == ',' && !emAspas) {
                partes.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        partes.add(atual.toString());
        return partes;
    }

    /** O valor pronto para ir no lugar do {@code ?}. */
    static String comAspas(String valor, String tipo) {
        String v = valor.strip();
        if (v.equalsIgnoreCase("null") || v.equals("<null>")) {
            return "NULL";
        }
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            return v;
        }
        if (tipo != null) {
            return TIPOS_SEM_ASPAS.contains(tipo.toUpperCase(Locale.ROOT)) ? v : "'" + v.replace("'", "''") + "'";
        }
        if (NUMERO.matcher(v).matches() || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return v;
        }
        return "'" + v.replace("'", "''") + "'";
    }

    // ===================================================== preencher os ?

    record Preenchido(String sql, int preenchidos, int semValor, int sobrando) {
    }

    /** Troca {@code ?} (em ordem) e {@code $n} (pelo número) — fora de aspas e comentários. */
    static Preenchido preencher(String sql, List<String> valores) {
        StringBuilder saida = new StringBuilder();
        int proximo = 0;
        int preenchidos = 0;
        int semValor = 0;
        boolean usouNumerado = false;
        for (Trecho t : trechos(sql)) {
            if (t.tipo() != Tipo.CODIGO) {
                saida.append(t.texto());
                continue;
            }
            String c = t.texto();
            for (int i = 0; i < c.length(); i++) {
                char ch = c.charAt(i);
                if (ch == '?') {
                    if (proximo < valores.size()) {
                        saida.append(valores.get(proximo++));
                        preenchidos++;
                    } else {
                        saida.append(ch);
                        semValor++;
                    }
                } else if (ch == '$' && i + 1 < c.length() && Character.isDigit(c.charAt(i + 1))) {
                    int fim = i + 1;
                    while (fim < c.length() && Character.isDigit(c.charAt(fim))) {
                        fim++;
                    }
                    int n = Integer.parseInt(c.substring(i + 1, fim));
                    usouNumerado = true;
                    if (n >= 1 && n <= valores.size()) {
                        saida.append(valores.get(n - 1));
                        preenchidos++;
                    } else {
                        saida.append(c, i, fim);
                        semValor++;
                    }
                    i = fim - 1;
                } else {
                    saida.append(ch);
                }
            }
        }
        int sobrando = usouNumerado ? 0 : Math.max(0, valores.size() - proximo);
        return new Preenchido(saida.toString(), preenchidos, semValor, sobrando);
    }

    // ============================================================ caixa

    /**
     * Formata e aplica a caixa só nas palavras-chave.
     *
     * <p>Como saber quais letras são de palavra-chave sem uma lista própria:
     * formatar duas vezes uma cópia com o código em minúsculas — uma pedindo
     * maiúsculas, outra não. Onde as duas diferem, é palavra-chave. As saídas
     * têm o mesmo desenho (caixa não muda onde a linha quebra), então a posição
     * vale para o texto formatado de verdade. Se algum dia não baterem, fica
     * como a biblioteca devolveu — nunca um texto embaralhado.
     */
    private static String formatarComCaixa(String sql, Banco banco, Caixa caixa, String recuo) {
        SqlFormatter.Formatter f = SqlFormatter.of(banco.dialeto);
        String original = f.format(sql, config(recuo, false));
        if (caixa == Caixa.COMO_VEIO) {
            return original;
        }
        String minusculo = minusculasNoCodigo(sql);
        String marcaMaiuscula = f.format(minusculo, config(recuo, true));
        String marcaComoVeio = f.format(minusculo, config(recuo, false));
        if (marcaMaiuscula.length() != original.length() || marcaComoVeio.length() != original.length()) {
            return caixa == Caixa.MAIUSCULAS ? f.format(sql, config(recuo, true)) : original;
        }
        StringBuilder saida = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            boolean palavraChave = marcaMaiuscula.charAt(i) != marcaComoVeio.charAt(i);
            saida.append(!palavraChave ? c
                    : caixa == Caixa.MAIUSCULAS ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return saida.toString();
    }

    private static FormatConfig config(String recuo, boolean maiusculas) {
        return FormatConfig.builder().indent(recuo).uppercase(maiusculas).linesBetweenQueries(2).build();
    }

    private static String minusculasNoCodigo(String sql) {
        StringBuilder s = new StringBuilder();
        for (Trecho t : trechos(sql)) {
            s.append(t.tipo() == Tipo.CODIGO ? t.texto().toLowerCase(Locale.ROOT) : t.texto());
        }
        return s.toString();
    }

    /** {@code id :: TEXT} volta a {@code id::TEXT}, só fora de aspas. */
    static String juntarCast(String sql) {
        StringBuilder s = new StringBuilder();
        for (Trecho t : trechos(sql)) {
            s.append(t.tipo() == Tipo.CODIGO ? t.texto().replace(" :: ", "::") : t.texto());
        }
        return s.toString();
    }

    // ========================================================= compactar

    static String compactarEspacos(String sql) {
        StringBuilder s = new StringBuilder();
        for (Trecho t : trechos(sql)) {
            switch (t.tipo()) {
                case CODIGO -> s.append(t.texto().replaceAll("\\s+", " "));
                // Numa linha só, "-- x" comeria o resto da consulta.
                case COMENTARIO_LINHA -> s.append("/* ").append(t.texto().substring(2).strip()).append(" */");
                default -> s.append(t.texto());
            }
        }
        String linha = s.toString().strip();
        // "( SELECT" e "x )", que sobram das quebras de linha, voltam a "(SELECT" e "x)".
        StringBuilder fim = new StringBuilder();
        for (Trecho t : trechos(linha)) {
            fim.append(t.tipo() == Tipo.CODIGO
                    ? t.texto().replace("( ", "(").replace(" )", ")").replaceAll(" +,", ",")
                    : t.texto());
        }
        return fim.toString().replaceAll(" {2,}", " ");
    }

    // ===================================================== aspas e comentários

    enum Tipo { CODIGO, TEXTO, COMENTARIO_LINHA, COMENTARIO_BLOCO }

    record Trecho(String texto, Tipo tipo) {
    }

    /**
     * Separa o que é código do que não se pode tocar: texto entre aspas
     * simples ('' dentro é uma aspa), nome entre aspas duplas ou crases, e
     * comentários {@code --} e {@code /* *\/}.
     */
    static List<Trecho> trechos(String sql) {
        List<Trecho> lista = new ArrayList<>();
        StringBuilder codigo = new StringBuilder();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            int fim = -1;
            Tipo tipo = null;
            if (c == '\'') {
                fim = i + 1;
                while (fim < n) {
                    if (sql.charAt(fim) == '\'') {
                        if (fim + 1 < n && sql.charAt(fim + 1) == '\'') {
                            fim += 2;
                            continue;
                        }
                        break;
                    }
                    fim++;
                }
                fim = Math.min(n, fim + 1);
                tipo = Tipo.TEXTO;
            } else if (c == '"' || c == '`') {
                int fecha = sql.indexOf(c, i + 1);
                fim = fecha < 0 ? n : fecha + 1;
                tipo = Tipo.TEXTO;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int quebra = sql.indexOf('\n', i);
                fim = quebra < 0 ? n : quebra;
                tipo = Tipo.COMENTARIO_LINHA;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int fecha = sql.indexOf("*/", i + 2);
                fim = fecha < 0 ? n : fecha + 2;
                tipo = Tipo.COMENTARIO_BLOCO;
            }
            if (tipo == null) {
                codigo.append(c);
                i++;
                continue;
            }
            if (codigo.length() > 0) {
                lista.add(new Trecho(codigo.toString(), Tipo.CODIGO));
                codigo.setLength(0);
            }
            lista.add(new Trecho(sql.substring(i, fim), tipo));
            i = fim;
        }
        if (codigo.length() > 0) {
            lista.add(new Trecho(codigo.toString(), Tipo.CODIGO));
        }
        return lista;
    }
}
