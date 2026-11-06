package br.com.myapp.modules.atalhos;

import br.com.myapp.ui.Icone;

/** O que um atalho abre. Define o "Procurar…" do cadastro e o símbolo reserva. */
public enum TipoAtalho {

    APLICATIVO("Aplicativo", Icone.Simbolo.MONITOR, "C:\\Program Files\\...\\programa.exe"),
    PASTA("Pasta", Icone.Simbolo.PASTA, "\\\\servidor\\compartilhamento ou C:\\pasta"),
    SITE("Site", Icone.Simbolo.GLOBO, "https://..."),
    ARQUIVO("Arquivo", Icone.Simbolo.TEXTO, "Planilha, documento, .bat...");

    public final String rotulo;
    /** Desenhado quando não há ícone do programa nem escolhido. */
    public final Icone.Simbolo simbolo;
    public final String exemplo;

    TipoAtalho(String rotulo, Icone.Simbolo simbolo, String exemplo) {
        this.rotulo = rotulo;
        this.simbolo = simbolo;
        this.exemplo = exemplo;
    }

    public static TipoAtalho doBanco(String nome) {
        try {
            return valueOf(nome);
        } catch (Exception e) {
            return ARQUIVO;
        }
    }
}
