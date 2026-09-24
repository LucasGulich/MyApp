package br.com.myapp.modules.notas;

import br.com.myapp.ui.Icone;

import java.util.List;

/**
 * O que a nota é.
 *
 * O tipo define três coisas: quais campos aparecem no formulário, quais deles
 * são tratados como segredo (cifrados sempre, exibidos como pontinhos) e
 * quais ações o aplicativo consegue oferecer — abrir um link, copiar uma
 * senha, destacar um trecho de código.
 *
 * É o que separa "um lugar para escrever" de uma ferramenta que ajuda: um
 * bloco de notas comum não sabe que aquele texto é uma senha, então não pode
 * escondê-la nem limpá-la da área de transferência.
 */
public enum TipoNota {

    LIVRE("Nota livre", Icone.Simbolo.TEXTO,
            "Texto solto, do jeito que você escreveria num bloco de notas.",
            List.of()),

    CREDENCIAL("Credencial", Icone.Simbolo.CREDENCIAL,
            "Usuário e senha de um sistema, com cópia segura e gerador.",
            List.of(
                    new Campo("Usuário", false),
                    new Campo("Senha", true),
                    new Campo("Endereço", false))),

    LINK("Link", Icone.Simbolo.ELO,
            "Um endereço que você acessa com frequência.",
            List.of(
                    new Campo("URL", false))),

    CODIGO("Trecho de código", Icone.Simbolo.CODIGO,
            "SQL, script ou comando que você reaproveita.",
            List.of(
                    new Campo("Linguagem", false)));

    /**
     * Um campo do formulário.
     *
     * @param nome     rótulo exibido e chave gravada no banco
     * @param sensivel se verdadeiro, o valor é sempre cifrado e exibido oculto
     */
    public record Campo(String nome, boolean sensivel) {
    }

    private final String rotulo;
    private final Icone.Simbolo icone;
    private final String explicacao;
    private final List<Campo> campos;

    TipoNota(String rotulo, Icone.Simbolo icone, String explicacao, List<Campo> campos) {
        this.rotulo = rotulo;
        this.icone = icone;
        this.explicacao = explicacao;
        this.campos = campos;
    }

    public String rotulo() {
        return rotulo;
    }

    public Icone.Simbolo icone() {
        return icone;
    }

    public String explicacao() {
        return explicacao;
    }

    /** Campos próprios deste tipo, na ordem em que aparecem. */
    public List<Campo> campos() {
        return campos;
    }

    /** Este tipo tem algum campo de segredo? */
    public boolean temSegredo() {
        return campos.stream().anyMatch(Campo::sensivel);
    }

    /** Nome do campo que guarda o segredo principal, se houver. */
    public String campoDeSegredo() {
        return campos.stream()
                .filter(Campo::sensivel)
                .map(Campo::nome)
                .findFirst()
                .orElse(null);
    }

    /** O corpo da nota é o conteúdo principal, e não uma observação? */
    public boolean corpoEPrincipal() {
        return this == LIVRE || this == CODIGO;
    }

    /** Rótulo do campo de texto grande, que muda conforme o tipo. */
    public String rotuloDoCorpo() {
        return switch (this) {
            case LIVRE -> "TEXTO";
            case CODIGO -> "CÓDIGO";
            case CREDENCIAL, LINK -> "OBSERVAÇÕES";
        };
    }

    @Override
    public String toString() {
        // O que aparece na lista de escolha do tipo. O ícone entra ao lado
        // pelo desenho da tela, e não por texto: aqui vai só o nome.
        return rotulo;
    }
}
