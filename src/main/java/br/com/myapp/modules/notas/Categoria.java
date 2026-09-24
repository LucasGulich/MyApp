package br.com.myapp.modules.notas;

import java.time.LocalDateTime;

/**
 * Uma pasta de notas, criada por você.
 *
 * Cada categoria tem nome, cor e ícone — a cor e o ícone existem para o
 * reconhecimento bater o olho, sem precisar ler. Uma categoria pode exigir a
 * senha mestra: nesse caso, o aplicativo trancado não mostra nem os títulos
 * do que está dentro dela.
 */
public class Categoria {

    private Long id;
    private String nome = "";
    private String cor = "#4C8DFF";
    /**
     * Chave do ícone no catálogo vetorial ({@code "pasta"}, {@code "banco"}…).
     *
     * Guarda-se a chave e não o desenho: assim trocar o traçado do ícone um
     * dia não obriga a reescrever o banco. Ver {@code Icone.porChave}.
     */
    private String icone = "pasta";

    /** Reservado para subcategorias. Ainda não usado pela interface. */
    private Long categoriaPaiId;

    private int ordem;

    /** Esconde a categoria inteira enquanto o aplicativo estiver trancado. */
    private boolean exigeDesbloqueio;

    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();
    private LocalDateTime dataExclusao;

    /** Quantas notas estão dentro. Preenchido nas consultas de listagem. */
    private int quantidadeDeNotas;

    public String validar() {
        if (nome == null || nome.isBlank()) {
            return "Dê um nome à categoria.";
        }
        if (nome.length() > 40) {
            return "O nome da categoria ficou longo demais (máximo de 40 caracteres).";
        }
        return null;
    }

    public boolean isExcluida() {
        return dataExclusao != null;
    }

    /** Como aparece no menu lateral do módulo. O ícone vai ao lado, na tela. */
    public String rotulo() {
        return nome;
    }

    // ------------------------------------------------------ getters/setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }

    public String getIcone() {
        return icone;
    }

    public void setIcone(String icone) {
        this.icone = (icone == null || icone.isBlank()) ? "pasta" : icone;
    }

    public Long getCategoriaPaiId() {
        return categoriaPaiId;
    }

    public void setCategoriaPaiId(Long categoriaPaiId) {
        this.categoriaPaiId = categoriaPaiId;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isExigeDesbloqueio() {
        return exigeDesbloqueio;
    }

    public void setExigeDesbloqueio(boolean exigeDesbloqueio) {
        this.exigeDesbloqueio = exigeDesbloqueio;
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

    public int getQuantidadeDeNotas() {
        return quantidadeDeNotas;
    }

    public void setQuantidadeDeNotas(int quantidadeDeNotas) {
        this.quantidadeDeNotas = quantidadeDeNotas;
    }

    @Override
    public String toString() {
        return nome;
    }
}
