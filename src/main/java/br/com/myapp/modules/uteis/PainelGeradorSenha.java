package br.com.myapp.modules.uteis;

import br.com.myapp.ui.GeradorSenhaForm;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Gerar uma senha sem precisar abrir uma nota — para a senha do Wi-Fi da sala
 * de reunião, o usuário novo no sistema do cliente, o arquivo .zip.
 *
 * <p>É o mesmo gerador do campo de senha das notas ({@link GeradorSenhaForm}).
 * Aqui só gera e copia: a cópia some da área de transferência sozinha em 30 s,
 * como toda senha copiada pelo MyApp.
 */
public class PainelGeradorSenha extends Secao {

    public PainelGeradorSenha() {
        super(Icone.Simbolo.CHAVE, "GERADOR DE SENHA",
                "Senha forte ou frase-senha, pronta para copiar. Nada é guardado.");

        GeradorSenhaForm gerador = new GeradorSenhaForm();
        Node resultado = gerador.resultado();
        Node opcoes = gerador.opcoes();
        // Não precisa da largura toda da tela: com a janela maximizada, o
        // campo da senha e o controle de tamanho ficariam compridos demais.
        for (Node n : new Node[]{resultado, opcoes}) {
            if (n instanceof Region r) {
                r.setMaxWidth(560);
            }
        }
        Region separador = new Region();
        separador.getStyleClass().add("separador");
        separador.setPrefHeight(1);
        VBox.setMargin(separador, new javafx.geometry.Insets(4, 0, 4, 0));

        com(resultado, separador, opcoes);
    }
}
