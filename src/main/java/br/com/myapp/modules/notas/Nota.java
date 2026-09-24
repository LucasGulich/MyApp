package br.com.myapp.modules.notas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uma anotação.
 *
 * O título e o corpo são comuns a todos os tipos; o que muda de um tipo para
 * outro vive na lista de campos, cada um com sua chave e seu valor.
 *
 * Uma nota marcada como protegida tem título, corpo e campos cifrados. Campos
 * de segredo (a senha de uma credencial) são cifrados sempre, mesmo em nota
 * não protegida — um valor chamado "Senha" não tem por que existir em texto
 * claro no banco, em nenhuma circunstância.
 */
public class Nota {

    private Long id;
    private Long categoriaId;

    private TipoNota tipo = TipoNota.LIVRE;
    private String titulo = "";

    /** Texto grande: o conteúdo em si, ou as observações, conforme o tipo. */
    private String corpo = "";

    private final List<CampoNota> campos = new ArrayList<>();

    private boolean protegida;
    private boolean favorita;
    private boolean fixada;

    /** Posição escolhida ao arrastar. -1 = nunca foi arrastada. */
    private int ordem = -1;

    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();
    private LocalDateTime dataExclusao;

    // --------------------------------------------------------------- campos

    /** Valor de um campo pelo nome. Vazio se o campo não existe. */
    public Optional<String> valor(String chave) {
        return campos.stream()
                .filter(c -> c.getChave().equalsIgnoreCase(chave))
                .map(CampoNota::getValor)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    /** Define (ou cria) um campo. */
    public void definirCampo(String chave, String valor, boolean sensivel) {
        for (CampoNota campo : campos) {
            if (campo.getChave().equalsIgnoreCase(chave)) {
                campo.setValor(valor);
                campo.setSensivel(sensivel);
                return;
            }
        }
        CampoNota novo = new CampoNota();
        novo.setChave(chave);
        novo.setValor(valor);
        novo.setSensivel(sensivel);
        novo.setOrdem(campos.size());
        campos.add(novo);
    }

    /**
     * Garante que os campos do tipo existam, na ordem certa.
     *
     * Chamado ao abrir o editor: uma nota que era livre e virou credencial
     * ganha os campos de usuário e senha sem perder o que já tinha.
     */
    public void ajustarCamposAoTipo() {
        int ordem = 0;
        for (TipoNota.Campo definicao : tipo.campos()) {
            Optional<CampoNota> existente = campos.stream()
                    .filter(c -> c.getChave().equalsIgnoreCase(definicao.nome()))
                    .findFirst();

            if (existente.isPresent()) {
                existente.get().setSensivel(definicao.sensivel());
                existente.get().setOrdem(ordem);
            } else {
                CampoNota novo = new CampoNota();
                novo.setChave(definicao.nome());
                novo.setValor("");
                novo.setSensivel(definicao.sensivel());
                novo.setOrdem(ordem);
                campos.add(novo);
            }
            ordem++;
        }
        campos.sort(java.util.Comparator.comparingInt(CampoNota::getOrdem));
    }

    /** Campos que o usuário criou, fora os previstos pelo tipo. */
    public List<CampoNota> camposExtras() {
        List<String> doTipo = tipo.campos().stream().map(TipoNota.Campo::nome).toList();
        return campos.stream()
                .filter(c -> doTipo.stream().noneMatch(n -> n.equalsIgnoreCase(c.getChave())))
                .toList();
    }

    // -------------------------------------------------------------- resumo

    /**
     * A linha de apoio na lista, que muda conforme o tipo.
     *
     * Nunca mostra o valor de um campo de segredo.
     */
    public String resumo() {
        return switch (tipo) {
            case CREDENCIAL -> valor("Usuário").map(u -> "usuário: " + u)
                    .orElse("credencial sem usuário");
            case LINK -> valor("URL").orElse("link sem endereço");
            case CODIGO -> valor("Linguagem").map(l -> l + " · " + contarLinhas() + " linha(s)")
                    .orElse(contarLinhas() + " linha(s)");
            case LIVRE -> primeiraLinhaDoCorpo();
        };
    }

    private int contarLinhas() {
        if (corpo == null || corpo.isEmpty()) {
            return 0;
        }
        return corpo.split("\r?\n", -1).length;
    }

    private String primeiraLinhaDoCorpo() {
        if (corpo == null || corpo.isBlank()) {
            return "nota vazia";
        }
        String primeira = corpo.strip().split("\r?\n")[0].strip();
        return primeira.length() > 90 ? primeira.substring(0, 90) + "…" : primeira;
    }

    /** Mensagem de erro, ou nulo se estiver tudo certo. */
    public String validar() {
        if (titulo == null || titulo.isBlank()) {
            return "Escreva um título para a nota.";
        }
        if (tipo == TipoNota.LINK && valor("URL").isEmpty()) {
            return "Informe o endereço do link.";
        }
        return null;
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

    public Long getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Long categoriaId) {
        this.categoriaId = categoriaId;
    }

    public TipoNota getTipo() {
        return tipo;
    }

    public void setTipo(TipoNota tipo) {
        this.tipo = tipo == null ? TipoNota.LIVRE : tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getCorpo() {
        return corpo;
    }

    public void setCorpo(String corpo) {
        this.corpo = corpo;
    }

    public List<CampoNota> getCampos() {
        return campos;
    }

    public boolean isProtegida() {
        return protegida;
    }

    public void setProtegida(boolean protegida) {
        this.protegida = protegida;
    }

    public boolean isFavorita() {
        return favorita;
    }

    public void setFavorita(boolean favorita) {
        this.favorita = favorita;
    }

    public boolean isFixada() {
        return fixada;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public void setFixada(boolean fixada) {
        this.fixada = fixada;
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
        return "Nota#" + id;   // sem o título: pode ser conteúdo protegido
    }
}
