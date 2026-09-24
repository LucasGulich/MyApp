package br.com.myapp.modules.inicio;

import java.time.LocalDateTime;

/**
 * Um recado colado na tela inicial.
 *
 * É o bilhete rápido: aquilo que você anotaria num papel adesivo e grudaria no
 * monitor. Não tem data, não alerta, não vira compromisso — se precisar disso,
 * o lugar é o módulo de lembretes.
 *
 * A ordem é a posição escolhida ao arrastar, e é o que faz o arranjo
 * sobreviver ao fechar o aplicativo.
 */
public class Recado {

    private Long id;
    private String texto = "";
    private CorRecado cor = CorRecado.AMARELO;
    private int ordem;

    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();
    private LocalDateTime dataExclusao;

    public String validar() {
        if (texto == null || texto.isBlank()) {
            return "Escreva alguma coisa no recado.";
        }
        if (texto.length() > 600) {
            return "O recado ficou longo demais. Para textos grandes, use as Notas.";
        }
        return null;
    }

    public boolean estaExcluido() {
        return dataExclusao != null;
    }

    /** Primeira linha, usada como resumo em espaços apertados. */
    public String primeiraLinha() {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.strip().split("\r?\n")[0].strip();
    }

    // ------------------------------------------------------ getters/setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto == null ? "" : texto;
    }

    public CorRecado getCor() {
        return cor;
    }

    public void setCor(CorRecado cor) {
        this.cor = cor == null ? CorRecado.AMARELO : cor;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public LocalDateTime getDataExclusao() {
        return dataExclusao;
    }

    public void setDataExclusao(LocalDateTime dataExclusao) {
        this.dataExclusao = dataExclusao;
    }

    @Override
    public String toString() {
        return "Recado#" + id;
    }
}
