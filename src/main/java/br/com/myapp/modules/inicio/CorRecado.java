package br.com.myapp.modules.inicio;

/**
 * As cores dos recados.
 *
 * São as cores clássicas dos blocos autoadesivos de papel — o amarelo antes de
 * todas, que é o que a maioria das pessoas espera ao pensar em um recado
 * colado no monitor.
 *
 * Cada cor traz três tons: o fundo, uma faixa mais forte para a borda de cima
 * (que imita a parte colada do papel) e o tom do texto. O texto é sempre
 * escuro, porque papel colorido com letra clara não se lê.
 */
public enum CorRecado {

    AMARELO("Amarelo", "#FFE27A", "#F5C518", "#4A3B00"),
    ROSA("Rosa", "#FFB3C6", "#FF7BA0", "#4A0018"),
    VERDE("Verde", "#B8E986", "#8BC34A", "#2A3D00"),
    AZUL("Azul", "#A8D8FF", "#5BB0F5", "#00304A"),
    LARANJA("Laranja", "#FFC48C", "#FF9A3C", "#4A2300"),
    ROXO("Roxo", "#D9B8FF", "#B47BFF", "#2E0A4A"),
    CIANO("Ciano", "#A8F0E6", "#4ED6C4", "#003D36"),
    CINZA("Cinza", "#DCE1E8", "#B4BCC9", "#2A2F38");

    private final String rotulo;
    private final String fundo;
    private final String faixa;
    private final String texto;

    CorRecado(String rotulo, String fundo, String faixa, String texto) {
        this.rotulo = rotulo;
        this.fundo = fundo;
        this.faixa = faixa;
        this.texto = texto;
    }

    public String rotulo() {
        return rotulo;
    }

    public String fundo() {
        return fundo;
    }

    public String faixa() {
        return faixa;
    }

    public String texto() {
        return texto;
    }

    /** Converte com segurança: um valor estranho no banco vira amarelo. */
    public static CorRecado de(String nome) {
        if (nome == null || nome.isBlank()) {
            return AMARELO;
        }
        try {
            return valueOf(nome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AMARELO;
        }
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
