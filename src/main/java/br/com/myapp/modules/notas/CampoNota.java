package br.com.myapp.modules.notas;

/**
 * Um campo de uma nota, no formato chave e valor.
 *
 * Guardar os campos como linhas, e não como colunas da tabela de notas, é o
 * que permite cada tipo ter os seus e você criar os seus próprios sem que o
 * banco precise mudar.
 */
public class CampoNota {

    private Long id;
    private String chave = "";
    private String valor = "";

    /**
     * Segredo.
     *
     * Um campo assim é cifrado sempre — mesmo que a nota inteira não esteja
     * marcada como protegida — e aparece oculto na tela, com botões para
     * revelar e para copiar.
     */
    private boolean sensivel;

    private int ordem;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave == null ? "" : chave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor == null ? "" : valor;
    }

    public boolean isSensivel() {
        return sensivel;
    }

    public void setSensivel(boolean sensivel) {
        this.sensivel = sensivel;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean estaVazio() {
        return valor == null || valor.isBlank();
    }

    @Override
    public String toString() {
        // Nunca devolve o valor: este objeto pode carregar uma senha.
        return chave + (sensivel ? " (segredo)" : "");
    }
}
