package br.com.myapp.modules.atalhos;

import java.util.ArrayList;
import java.util.List;

/** Um bloco da tela de atalhos: "Aplicativos do dia a dia", "Pastas na rede"... */
public class GrupoAtalho {

    private Long id;
    private String nome = "";
    private int ordem;
    private final List<Atalho> atalhos = new ArrayList<>();

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
        this.nome = nome == null ? "" : nome;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public List<Atalho> getAtalhos() {
        return atalhos;
    }
}
