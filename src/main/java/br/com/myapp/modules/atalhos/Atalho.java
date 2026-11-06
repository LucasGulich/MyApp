package br.com.myapp.modules.atalhos;

/**
 * Um atalho: algo que se abre com um clique — programa, pasta, site ou arquivo.
 *
 * <p>O ícone tem três origens ({@link ModoIcone}). O do programa ou do site é
 * guardado já pronto, em PNG, em {@link #getIconePng()}.
 */
public class Atalho {

    /** De onde vem o desenho do bloco. */
    public enum ModoIcone {
        /** O ícone do próprio programa, pasta ou site. */
        AUTOMATICO,
        /** Um símbolo do catálogo do MyApp, numa cor. */
        SIMBOLO,
        /** Uma imagem escolhida pela pessoa. */
        IMAGEM;

        static ModoIcone doBanco(String nome) {
            try {
                return valueOf(nome);
            } catch (Exception e) {
                return AUTOMATICO;
            }
        }
    }

    private Long id;
    private long grupoId;
    private String nome = "";
    private TipoAtalho tipo = TipoAtalho.APLICATIVO;
    private String alvo = "";
    private String argumentos = "";
    private ModoIcone modoIcone = ModoIcone.AUTOMATICO;
    private byte[] iconePng;
    private String iconeSimbolo;
    private String cor;
    private int ordem;
    private int usos;
    /** Site: o .exe do navegador escolhido. Vazio é o padrão do Windows. */
    private String navegador = "";

    public String getNavegador() {
        return navegador;
    }

    public void setNavegador(String navegador) {
        this.navegador = navegador == null ? "" : navegador;
    }

    public Atalho copia() {
        Atalho c = new Atalho();
        c.grupoId = grupoId;
        c.nome = nome;
        c.tipo = tipo;
        c.alvo = alvo;
        c.argumentos = argumentos;
        c.modoIcone = modoIcone;
        c.iconePng = iconePng == null ? null : iconePng.clone();
        c.iconeSimbolo = iconeSimbolo;
        c.cor = cor;
        c.navegador = navegador;
        return c;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(long grupoId) {
        this.grupoId = grupoId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome == null ? "" : nome;
    }

    public TipoAtalho getTipo() {
        return tipo;
    }

    public void setTipo(TipoAtalho tipo) {
        this.tipo = tipo;
    }

    public String getAlvo() {
        return alvo;
    }

    public void setAlvo(String alvo) {
        this.alvo = alvo == null ? "" : alvo;
    }

    public String getArgumentos() {
        return argumentos;
    }

    public void setArgumentos(String argumentos) {
        this.argumentos = argumentos == null ? "" : argumentos;
    }

    public ModoIcone getModoIcone() {
        return modoIcone;
    }

    public void setModoIcone(ModoIcone modoIcone) {
        this.modoIcone = modoIcone;
    }

    public byte[] getIconePng() {
        return iconePng;
    }

    public void setIconePng(byte[] iconePng) {
        this.iconePng = iconePng;
    }

    public String getIconeSimbolo() {
        return iconeSimbolo;
    }

    public void setIconeSimbolo(String iconeSimbolo) {
        this.iconeSimbolo = iconeSimbolo;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public int getUsos() {
        return usos;
    }

    public void setUsos(int usos) {
        this.usos = usos;
    }
}
