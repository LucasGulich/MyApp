package br.com.myapp.modules.kanban;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um cartão do quadro: uma tarefa, uma anotação, algo a fazer.
 *
 * <h2>Sobre o prazo</h2>
 *
 * É guardado como {@link LocalDate}, e no banco como texto ISO
 * ({@code aaaa-mm-dd}). Um prazo é um dia do calendário, não um instante: se
 * fosse carimbo de tempo, "vence dia 20" viraria "vence dia 19 às 21h" para
 * quem mudasse de fuso, e a comparação com "hoje" passaria a depender da hora
 * em que se olha. Texto ISO ainda tem a vantagem de ordenar por comparação de
 * texto, igual à comparação de datas.
 *
 * <h2>Sobre arquivar</h2>
 *
 * Arquivar não é excluir. O cartão sai da vista, mas continua na coluna e
 * volta a aparecer ao ligar "mostrar arquivados". É o gesto para o que foi
 * concluído e não precisa mais ocupar espaço — enquanto excluir é para o que
 * não devia ter sido criado.
 */
public class CardKanban {

    private Long id;
    private Long colunaId;

    private String titulo = "";
    private String descricao = "";
    private LocalDate prazo;
    private CorKanban cor = CorKanban.NENHUMA;
    private int ordem;
    private boolean arquivado;

    /** Cifra título e descrição com a senha mestra. */
    private boolean protegido;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime dataExclusao;

    /** Mensagem pronta para a tela, ou nulo se estiver tudo certo. */
    public String validar() {
        if (titulo == null || titulo.isBlank()) {
            return "O cartão precisa de um título.";
        }
        if (titulo.length() > 200) {
            return "O título passou de 200 caracteres. Use a descrição para o resto.";
        }
        if (descricao != null && descricao.length() > 4000) {
            return "A descrição passou de 4000 caracteres.";
        }
        return null;
    }

    /** O prazo já passou? Hoje ainda não conta como vencido. */
    public boolean vencido() {
        return prazo != null && prazo.isBefore(LocalDate.now());
    }

    /** O prazo é hoje? */
    public boolean venceHoje() {
        return prazo != null && prazo.isEqual(LocalDate.now());
    }

    public boolean isExcluido() {
        return dataExclusao != null;
    }

    /** Uma cópia para a duplicação, sem identidade e sem histórico. */
    public CardKanban copia() {
        CardKanban c = new CardKanban();
        c.setColunaId(colunaId);
        c.setTitulo(titulo + " (cópia)");
        c.setDescricao(descricao);
        c.setPrazo(prazo);
        c.setCor(cor);
        c.setProtegido(protegido);
        return c;
    }

    // ------------------------------------------------------ getters/setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getColunaId() {
        return colunaId;
    }

    public void setColunaId(Long colunaId) {
        this.colunaId = colunaId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo == null ? "" : titulo.trim();
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao == null ? "" : descricao;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public void setPrazo(LocalDate prazo) {
        this.prazo = prazo;
    }

    public CorKanban getCor() {
        return cor;
    }

    public void setCor(CorKanban cor) {
        this.cor = cor == null ? CorKanban.NENHUMA : cor;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isArquivado() {
        return arquivado;
    }

    public void setArquivado(boolean arquivado) {
        this.arquivado = arquivado;
    }

    public boolean isProtegido() {
        return protegido;
    }

    public void setProtegido(boolean protegido) {
        this.protegido = protegido;
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
        return titulo;
    }
}
