package br.com.myapp.ui;

import br.com.myapp.security.SecurityService;
import br.com.myapp.security.SenhaIncorretaException;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Tela de senha, nos dois papeis:
 *
 *  - CADASTRO: primeira abertura do aplicativo, define a senha mestra;
 *  - BLOQUEIO: aplicativo trancado, pede a senha para liberar.
 *
 * Detalhe importante de segurança: a senha e lida como char[] e apagada logo
 * após o uso. Se ela virasse String, ficaria na memória fora do nosso controle
 * até o coletor de lixo decidir remove-la.
 */
public class TelaSenha extends VBox {

    public enum Modo { CADASTRO, BLOQUEIO }

    private final Modo modo;
    private final Consumer<Void> aoLiberar;

    private final PasswordField campoSenha = new PasswordField();
    /** Mesmo conteúdo do campo acima, exibido em texto quando o usuário pede. */
    private final TextField campoSenhaVisivel = new TextField();
    private final PasswordField campoConfirmacao = new PasswordField();
    private final TextField campoDica = new TextField();
    private final MedidorForca medidorForca = new MedidorForca();
    private final Label rotuloForca = new Label();
    private final Label mensagemErro = new Label();
    private final Button botaoPrincipal = new Button();
    /** Alterna entre esconder e mostrar a senha digitada. */
    private final Button botaoOlho = new Button();

    public TelaSenha(Modo modo, Consumer<Void> aoLiberar) {
        this.modo = modo;
        this.aoLiberar = aoLiberar;
        getStyleClass().add("tela-bloqueio");
        montar();
    }

    private void montar() {
        Node cadeado = Icone.de(
                modo == Modo.CADASTRO ? Icone.Simbolo.ESCUDO : Icone.Simbolo.CADEADO,
                64, "cadeado");

        Label titulo = new Label(modo == Modo.CADASTRO
                ? "Proteja o seu MyApp"
                : "Aplicativo bloqueado");
        titulo.getStyleClass().add("titulo-tela");

        Label explicacao = new Label(modo == Modo.CADASTRO
                ? """
                  Crie a senha mestra. Ela protege tudo que você marcar como \
                  conteúdo protegido dentro do aplicativo.

                  A senha não é gravada em lugar nenhum: se você esquecê-la, \
                  não há como recuperar o conteúdo protegido. Guarde-a bem."""
                : "Digite a senha mestra para continuar.");
        explicacao.getStyleClass().add("subtitulo");
        explicacao.setWrapText(true);
        explicacao.setMaxWidth(420);
        explicacao.setAlignment(Pos.CENTER);
        explicacao.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        campoSenha.setPromptText("Senha mestra");
        campoSenha.getStyleClass().addAll("campo", "campo-grande");
        campoSenha.setMaxWidth(340);

        // Os dois campos compartilham o texto; apenas um fica visível por vez.
        campoSenhaVisivel.setPromptText("Senha mestra");
        campoSenhaVisivel.getStyleClass().addAll("campo", "campo-grande");
        campoSenhaVisivel.setMaxWidth(340);
        campoSenhaVisivel.setVisible(false);
        campoSenhaVisivel.setManaged(false);
        campoSenhaVisivel.textProperty().bindBidirectional(campoSenha.textProperty());
        campoSenhaVisivel.setOnAction(e -> confirmar());

        StackPane pilhaSenha = new StackPane(campoSenha, campoSenhaVisivel);
        HBox.setHgrow(pilhaSenha, Priority.ALWAYS);

        // O olho fica ao lado do campo, e não numa caixa de seleção abaixo:
        // é onde a pessoa procura por ele, e vale nos dois modos da tela.
        botaoOlho.getStyleClass().addAll("botao-icone", "botao-olho");
        botaoOlho.setGraphic(Icone.de(Icone.Simbolo.OLHO, 19));
        botaoOlho.setTooltip(new Tooltip("Mostrar a senha"));
        botaoOlho.setFocusTraversable(false);
        botaoOlho.setOnAction(e -> alternarVisibilidade(!campoSenhaVisivel.isVisible()));

        HBox linhaSenha = new HBox(8, pilhaSenha, botaoOlho);
        linhaSenha.setAlignment(Pos.CENTER);
        linhaSenha.setMaxWidth(392);

        mensagemErro.getStyleClass().add("texto-perigo");
        mensagemErro.setWrapText(true);
        mensagemErro.setMaxWidth(340);
        mensagemErro.setAlignment(Pos.CENTER);
        mensagemErro.setVisible(false);

        botaoPrincipal.setText(modo == Modo.CADASTRO ? "Criar senha e entrar" : "Destrancar");
        botaoPrincipal.getStyleClass().add("botao-primario");
        botaoPrincipal.setMaxWidth(340);
        botaoPrincipal.setPrefWidth(340);
        botaoPrincipal.setDefaultButton(true);
        botaoPrincipal.setOnAction(e -> confirmar());

        getChildren().addAll(cadeado, titulo, explicacao, linhaSenha);

        if (modo == Modo.CADASTRO) {
            montarExtrasDoCadastro();
        } else {
            montarExtrasDoBloqueio();
        }

        getChildren().addAll(mensagemErro, botaoPrincipal);

        campoSenha.setOnAction(e -> confirmar());
        campoSenha.textProperty().addListener((o, a, n) -> {
            mensagemErro.setVisible(false);
            if (modo == Modo.CADASTRO) {
                atualizarForca(n);
            }
        });

        Platform.runLater(campoSenha::requestFocus);
    }

    private void montarExtrasDoCadastro() {
        rotuloForca.getStyleClass().add("texto-fraco");
        rotuloForca.setText("Use pelo menos 8 caracteres, misturando letras e números.");
        rotuloForca.setWrapText(true);
        rotuloForca.setMaxWidth(340);

        campoConfirmacao.setPromptText("Repita a senha");
        campoConfirmacao.getStyleClass().addAll("campo", "campo-grande");
        campoConfirmacao.setMaxWidth(340);
        campoConfirmacao.setOnAction(e -> confirmar());

        campoDica.setPromptText("Dica opcional (não escreva a senha aqui)");
        campoDica.getStyleClass().add("campo");
        campoDica.setMaxWidth(340);

        getChildren().addAll(medidorForca, rotuloForca, campoConfirmacao, campoDica);
    }

    private void montarExtrasDoBloqueio() {
        String dica = SecurityService.dica();
        if (dica != null && !dica.isBlank()) {
            Label rotuloDica = new Label("Dica: " + dica);
            rotuloDica.getStyleClass().add("texto-fraco");
            rotuloDica.setMaxWidth(340);
            rotuloDica.setWrapText(true);
            getChildren().add(rotuloDica);
        }
    }

    /**
     * Alterna entre esconder e mostrar a senha.
     *
     * O PasswordField do JavaFX não tem esse recurso pronto. A solução usual é
     * manter dois campos empilhados com o mesmo texto e mostrar um de cada vez:
     * o PasswordField, que exibe pontos, e um TextField comum.
     */
    private void alternarVisibilidade(boolean visivel) {
        campoSenha.setVisible(!visivel);
        campoSenha.setManaged(!visivel);
        campoSenhaVisivel.setVisible(visivel);
        campoSenhaVisivel.setManaged(visivel);

        botaoOlho.setGraphic(Icone.de(
                visivel ? Icone.Simbolo.OLHO_FECHADO : Icone.Simbolo.OLHO, 19));
        botaoOlho.getTooltip().setText(visivel ? "Esconder a senha" : "Mostrar a senha");

        // Mantém o cursor onde o usuário estava digitando.
        if (visivel) {
            campoSenhaVisivel.requestFocus();
            campoSenhaVisivel.end();
        } else {
            campoSenha.requestFocus();
            campoSenha.end();
        }
    }

    private void atualizarForca(String texto) {
        char[] senha = texto.toCharArray();
        int nota = SecurityService.forca(senha);
        java.util.Arrays.fill(senha, '\0');

        medidorForca.definir(texto.isEmpty() ? 0 : nota);

        if (texto.isEmpty()) {
            rotuloForca.setText("Use pelo menos 8 caracteres, misturando letras e números.");
        } else if (nota <= 1) {
            rotuloForca.setText("Senha fraca. Uma frase longa é mais segura e mais fácil de lembrar.");
        } else if (nota <= 2) {
            rotuloForca.setText("Razoável. Alongue um pouco mais para ficar bem protegida.");
        } else if (nota == 3) {
            rotuloForca.setText("Boa senha.");
        } else {
            rotuloForca.setText("Senha muito boa.");
        }
    }

    // ------------------------------------------------------------ confirmacao

    private void confirmar() {
        char[] senha = extrair(campoSenha);
        try {
            if (modo == Modo.CADASTRO) {
                char[] confirmacao = extrair(campoConfirmacao);
                try {
                    if (!java.util.Arrays.equals(senha, confirmacao)) {
                        mostrarErro("As duas senhas não são iguais.");
                        return;
                    }
                } finally {
                    java.util.Arrays.fill(confirmacao, '\0');
                }
                String dica = campoDica.getText();
                SecurityService.definirSenha(senha, dica == null || dica.isBlank() ? null : dica.trim());
            } else {
                SecurityService.destrancar(senha);
            }
            limparCampos();
            aoLiberar.accept(null);

        } catch (SenhaIncorretaException e) {
            mostrarErro("Senha incorreta.");
        } catch (IllegalArgumentException e) {
            mostrarErro(e.getMessage());
        } catch (Exception e) {
            mostrarErro("Não foi possível continuar: " + e.getMessage());
        } finally {
            java.util.Arrays.fill(senha, '\0');
        }
    }

    private char[] extrair(PasswordField campo) {
        // O JavaFX só expoe o conteúdo como String; convertemos imediatamente
        // para char[] e trabalhamos só com ele daqui em diante.
        String texto = campo.getText();
        return texto == null ? new char[0] : texto.toCharArray();
    }

    private void limparCampos() {
        campoSenha.clear();
        campoConfirmacao.clear();
        campoDica.clear();
        mensagemErro.setVisible(false);
    }

    private void mostrarErro(String texto) {
        mensagemErro.setText(texto);
        mensagemErro.setVisible(true);
        campoSenha.requestFocus();
        campoSenha.selectAll();
    }

    /** Limpa os campos ao reexibir a tela de bloqueio. */
    public void reiniciar() {
        limparCampos();
        Platform.runLater(campoSenha::requestFocus);
    }

    /** Linha de botoes auxiliares, usada por quem embute esta tela. */
    public HBox rodape(Button... botoes) {
        HBox linha = new HBox(10, botoes);
        linha.setAlignment(Pos.CENTER);
        return linha;
    }
}
