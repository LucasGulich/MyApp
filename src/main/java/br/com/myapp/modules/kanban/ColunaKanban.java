package br.com.myapp.modules.kanban;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma coluna do quadro: "A fazer", "Em andamento", "Concluído".
 *
 * <p>A coluna carrega os próprios cartões. O quadro inteiro é lido de uma vez
 * — colunas e cartões na mesma consulta — porque um Kanban só faz sentido
 * visto inteiro, e carregar cada coluna sob demanda daria uma tela que se
 * monta aos pedaços na frente de quem olha.
 */
public class ColunaKanban {

    private Long id;
    private String nome = "";
    private CorKanban cor = CorKanban.NENHUMA;
    private int ordem;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime dataExclusao;

    private final List<CardKanban> cards = new ArrayList<>();

    /** Mensagem pronta para a tela, ou nulo se estiver tudo certo. */
    public String validar() {
        if (nome == null || nome.isBlank()) {
            return "A coluna precisa de um nome.";
        }
        if (nome.length() > 60) {
            return "O nome da coluna passou de 60 caracteres.";
        }
        return null;
    }

    /** Cartões que aparecem, conforme a escolha de mostrar ou não arquivados. */
    public List<CardKanban> visiveis(boolean comArquivados) {
        return comArquivados
                ? List.copyOf(cards)
                : cards.stream().filter(c -> !c.isArquivado()).toList();
    }

    public long quantosArquivados() {
        return cards.stream().filter(CardKanban::isArquivado).count();
    }

    public boolean isExcluida() {
        return dataExclusao != null;
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
        this.nome = nome == null ? "" : nome.trim();
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

    public List<CardKanban> getCards() {
        return cards;
    }

    @Override
    public String toString() {
        return nome;
    }
}
