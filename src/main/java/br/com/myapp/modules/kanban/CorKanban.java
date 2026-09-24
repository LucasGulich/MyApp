package br.com.myapp.modules.kanban;

/**
 * A etiqueta de cor de uma coluna ou de um cartão.
 *
 * <p>São seis, e a ausência de cor é a sétima opção — a mais usada, e por
 * isso a primeira da lista. Um quadro em que tudo tem cor não destaca nada;
 * a cor serve para separar o que foge da rotina daquilo que é rotina.
 *
 * <p>Cada cor tem dois tons: a <b>faixa</b>, forte, que aparece como um risco
 * na lateral do cartão ou como a linha do cabeçalho da coluna; e o
 * <b>fundo</b>, muito diluído, usado atrás do cartão. Manter o fundo quase
 * imperceptível é o que permite pintar vários cartões sem transformar o
 * quadro em um mostruário de tintas.
 *
 * <p>As cores estão aqui, e não no CSS, pelo mesmo motivo dos recados: sete
 * opções por dois tons dariam catorze regras de estilo quase iguais, e ainda
 * seria preciso escrevê-las de novo para o tema claro.
 */
public enum CorKanban {

    NENHUMA("Sem cor", null, null),
    AZUL("Azul", "#4A8CFF", "rgba(74, 140, 255, 0.10)"),
    VERDE("Verde", "#3DD68C", "rgba(61, 214, 140, 0.10)"),
    AMBAR("Âmbar", "#F5B547", "rgba(245, 181, 71, 0.10)"),
    VERMELHO("Vermelho", "#FF5F6D", "rgba(255, 95, 109, 0.10)"),
    ROXO("Roxo", "#A97BFF", "rgba(169, 123, 255, 0.10)"),
    TURQUESA("Turquesa", "#36C7C7", "rgba(54, 199, 199, 0.10)");

    private final String rotulo;
    private final String faixa;
    private final String fundo;

    CorKanban(String rotulo, String faixa, String fundo) {
        this.rotulo = rotulo;
        this.faixa = faixa;
        this.fundo = fundo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** O tom forte: risco na lateral do cartão, linha da coluna. */
    public String faixa() {
        return faixa;
    }

    /** O tom diluído, atrás do cartão. */
    public String fundo() {
        return fundo;
    }

    public boolean temCor() {
        return faixa != null;
    }

    /**
     * Converte o que está gravado no banco.
     *
     * <p>Nome desconhecido ou nulo vira {@link #NENHUMA}: um cartão nunca
     * deixa de aparecer por causa de uma cor que este programa não reconhece.
     */
    public static CorKanban de(String nome) {
        if (nome == null || nome.isBlank()) {
            return NENHUMA;
        }
        try {
            return valueOf(nome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NENHUMA;
        }
    }

    /** O que vai para o banco. Sem cor grava nulo, e não a palavra "NENHUMA". */
    public String paraBanco() {
        return this == NENHUMA ? null : name();
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
