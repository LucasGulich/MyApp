package br.com.myapp.modules.notas;

import br.com.myapp.core.Config;
import br.com.myapp.security.GeradorSenha;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Janela de cadastro e edição de uma nota.
 *
 * O formulário se reorganiza conforme o tipo escolhido: uma credencial mostra
 * usuário e senha, um link mostra o endereço, um trecho de código mostra o
 * seletor de linguagem. Trocar o tipo não perde o que já foi digitado.
 */
public class EditorNota {

    private final Window dono;
    private final Nota nota;
    private final boolean ehNova;
    private final NotaService servico;
    private final List<Categoria> categorias;

    // --- campos fixos ---
    private final TextField campoTitulo = new TextField();
    private final ComboBox<TipoNota> campoTipo = new ComboBox<>();
    private final ComboBox<Categoria> campoCategoria = new ComboBox<>();
    private final TextArea campoCorpo = new TextArea();
    private final CheckBox campoProtegida = new CheckBox("Proteger esta nota com a senha mestra");

    // --- campos do tipo, montados conforme a escolha ---
    private final Map<String, TextField> camposComuns = new LinkedHashMap<>();
    private final Map<String, PasswordField> camposSegredo = new LinkedHashMap<>();
    private final Map<String, TextField> segredosVisiveis = new LinkedHashMap<>();

    private final ComboBox<String> campoLinguagem = new ComboBox<>();

    private Secao secaoCampos;
    private Secao secaoCorpo;
    private Label rotuloCorpo;

    public EditorNota(Window dono, Nota existente, NotaService servico, List<Categoria> categorias) {
        this(dono, existente, servico, categorias, null);
    }

    /**
     * @param categoriaInicial categoria já escolhida quando a nota é nova —
     *                         a que estava aberta na tela. Continua podendo
     *                         ser trocada no formulário. Ignorada ao editar.
     */
    public EditorNota(Window dono, Nota existente, NotaService servico, List<Categoria> categorias,
                      Long categoriaInicial) {
        this.dono = dono;
        this.ehNova = existente == null;
        this.servico = servico;
        this.categorias = categorias;
        this.nota = existente == null ? new Nota() : existente;
        if (ehNova && categoriaInicial != null) {
            this.nota.setCategoriaId(categoriaInicial);
        }
        this.nota.ajustarCamposAoTipo();
    }

    /** Abre a janela e devolve a nota preenchida, ou vazio se cancelou. */
    public Optional<Nota> abrir() {
        Dialog<Nota> dialogo = new Dialog<>();
        dialogo.setTitle(ehNova ? "Nova nota" : "Editar nota");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);

        ButtonType salvar = new ButtonType(ehNova ? "Criar nota" : "Salvar",
                ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);

        ScrollPane rolagem = new ScrollPane(montarFormulario());
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem-formulario");
        rolagem.setPrefViewportWidth(660);
        rolagem.setPrefViewportHeight(640);
        dialogo.getDialogPane().setContent(rolagem);

        Dialogos.aplicarTema(dialogo.getDialogPane());

        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");

        carregarValores();

        dialogo.setResultConverter(botao -> botao == salvar ? coletarValores() : null);
        return Optional.ofNullable(dialogo.showAndWait().orElse(null));
    }

    // ------------------------------------------------------------ formulário

    private VBox montarFormulario() {
        VBox forma = new VBox(18);
        forma.getStyleClass().add("formulario");
        if ("claro".equals(Config.get().tema)) {
            forma.getStyleClass().add("claro");
        }

        secaoCampos = new Secao(Icone.Simbolo.ORDENAR, "DADOS", "");
        secaoCorpo = montarSecaoCorpo();

        forma.getChildren().addAll(
                montarSecaoIdentificacao(),
                secaoCampos,
                secaoCorpo,
                montarSecaoSeguranca());

        return forma;
    }

    private Secao montarSecaoIdentificacao() {
        campoTitulo.setPromptText("Ex.: Acesso ao portal do cliente");
        campoTitulo.getStyleClass().addAll("campo", "campo-grande");

        campoTipo.getItems().addAll(TipoNota.values());
        campoTipo.getStyleClass().add("campo");
        campoTipo.setPrefWidth(240);
        campoTipo.setButtonCell(celulaDeTipo());
        campoTipo.setCellFactory(lista -> celulaDeTipo());
        campoTipo.setOnAction(e -> aoTrocarTipo());

        campoCategoria.getStyleClass().add("campo");
        campoCategoria.setPrefWidth(240);
        campoCategoria.getItems().add(null);   // opção "sem categoria"
        campoCategoria.getItems().addAll(categorias);
        campoCategoria.setButtonCell(celulaDeCategoria());
        campoCategoria.setCellFactory(lista -> celulaDeCategoria());

        HBox linha = new HBox(18,
                Secao.comRotulo("Tipo", campoTipo),
                Secao.comRotulo("Categoria", campoCategoria));
        linha.setAlignment(Pos.BOTTOM_LEFT);

        return new Secao(Icone.Simbolo.FIXAR, "IDENTIFICAÇÃO",
                Secao.comRotulo("Título", campoTitulo),
                linha);
    }

    private javafx.scene.control.ListCell<Categoria> celulaDeCategoria() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Categoria item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? "— sem categoria —" : item.rotulo());
                setGraphic(vazio || item == null
                        ? null
                        : Icone.deCategoria(item.getIcone(), 15));
            }
        };
    }

    /** A escolha do tipo mostra o mesmo ícone que a nota terá na lista. */
    private javafx.scene.control.ListCell<TipoNota> celulaDeTipo() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TipoNota item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? null : item.rotulo());
                setGraphic(vazio || item == null ? null : Icone.de(item.icone(), 15));
            }
        };
    }

    private Secao montarSecaoCorpo() {
        campoCorpo.getStyleClass().add("campo");
        campoCorpo.setWrapText(true);
        campoCorpo.setPrefRowCount(10);

        rotuloCorpo = Secao.campo("TEXTO");

        VBox caixa = new VBox(6, rotuloCorpo, campoCorpo);
        VBox.setVgrow(campoCorpo, Priority.ALWAYS);

        return new Secao(Icone.Simbolo.TEXTO, "CONTEÚDO", caixa);
    }

    private Secao montarSecaoSeguranca() {
        campoProtegida.setTooltip(new Tooltip(
                "Título, conteúdo e campos ficam cifrados e só aparecem com o app destrancado."));
        campoProtegida.setDisable(!SecurityService.estaDestrancado());
        if (!SecurityService.estaDestrancado()) {
            campoProtegida.setText("Proteger com a senha mestra (destranque o app para usar)");
        }

        return new Secao(Icone.Simbolo.ESCUDO, "SEGURANÇA",
                campoProtegida,
                Secao.dica("Campos de senha são cifrados sempre, mesmo sem marcar esta opção."));
    }

    // ------------------------------------------------- campos por tipo

    /**
     * Refaz a seção de dados quando o tipo muda.
     *
     * O que já foi digitado é preservado: os valores são lidos da tela antes
     * de reconstruir, e devolvidos aos campos de mesmo nome.
     */
    private void aoTrocarTipo() {
        recolherCamposDaTela();
        montarCamposDoTipo();
        ajustarCorpoAoTipo();
    }

    private void montarCamposDoTipo() {
        TipoNota tipo = campoTipo.getValue() == null ? TipoNota.LIVRE : campoTipo.getValue();

        camposComuns.clear();
        camposSegredo.clear();
        segredosVisiveis.clear();
        secaoCampos.corpo().getChildren().clear();

        if (tipo.campos().isEmpty()) {
            secaoCampos.mostrar(false);
            return;
        }
        secaoCampos.mostrar(true);

        for (TipoNota.Campo definicao : tipo.campos()) {
            if (definicao.sensivel()) {
                secaoCampos.corpo().getChildren().add(montarCampoSegredo(definicao.nome()));
            } else if (definicao.nome().equals("Linguagem")) {
                secaoCampos.corpo().getChildren().add(montarCampoLinguagem());
            } else {
                secaoCampos.corpo().getChildren().add(montarCampoComum(definicao.nome()));
            }
        }

        if (tipo == TipoNota.CREDENCIAL) {
            secaoCampos.corpo().getChildren().add(Secao.dica(
                    "A senha é cifrada no banco e, ao copiar, some da área de transferência "
                            + "em " + AreaTransferencia.SEGUNDOS_ATE_LIMPAR + " segundos."));
        }
        if (tipo == TipoNota.LINK) {
            secaoCampos.corpo().getChildren().add(Secao.dica(
                    "Pode digitar sem o https:// — o aplicativo completa ao abrir."));
        }
    }

    private VBox montarCampoComum(String nome) {
        TextField campo = new TextField();
        campo.getStyleClass().add("campo");
        campo.setPromptText(dicaDoCampo(nome));
        camposComuns.put(nome, campo);

        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR, "Copiar " + nome.toLowerCase());
        copiar.setOnAction(e -> AreaTransferencia.copiar(campo.getText()));

        HBox linha = new HBox(8, campo, copiar);
        HBox.setHgrow(campo, Priority.ALWAYS);
        linha.setAlignment(Pos.CENTER_LEFT);

        return new VBox(6, Secao.campo(nome), linha);
    }

    private VBox montarCampoLinguagem() {
        campoLinguagem.getItems().setAll(DestacadorSintaxe.LINGUAGENS);
        campoLinguagem.getStyleClass().add("campo");
        campoLinguagem.setPrefWidth(220);
        if (campoLinguagem.getValue() == null) {
            campoLinguagem.setValue("Texto");
        }
        return new VBox(6, Secao.campo("Linguagem"), campoLinguagem);
    }

    /**
     * Campo de segredo: escondido por padrão, com revelar, copiar e gerar.
     *
     * Dois controles empilhados — um {@code PasswordField} e um
     * {@code TextField} com o mesmo texto — porque o JavaFX não tem um campo
     * que alterne entre esconder e mostrar.
     */
    private VBox montarCampoSegredo(String nome) {
        PasswordField oculto = new PasswordField();
        oculto.getStyleClass().add("campo");
        oculto.setPromptText("••••••••");

        TextField visivel = new TextField();
        visivel.getStyleClass().add("campo");
        visivel.setVisible(false);
        visivel.setManaged(false);
        visivel.textProperty().bindBidirectional(oculto.textProperty());

        camposSegredo.put(nome, oculto);
        segredosVisiveis.put(nome, visivel);

        StackPane pilha = new StackPane(oculto, visivel);
        HBox.setHgrow(pilha, Priority.ALWAYS);

        Button ver = Botoes.icone(Icone.Simbolo.OLHO, "Mostrar ou esconder");
        ver.setOnAction(e -> {
            boolean mostrando = visivel.isVisible();
            visivel.setVisible(!mostrando);
            visivel.setManaged(!mostrando);
            oculto.setVisible(mostrando);
            oculto.setManaged(mostrando);
        });

        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR,
                "Copiar — some da área de transferência em "
                        + AreaTransferencia.SEGUNDOS_ATE_LIMPAR + " s");
        copiar.setOnAction(e -> AreaTransferencia.copiarSegredo(oculto.getText()));

        Button gerar = Botoes.icone(Icone.Simbolo.GERAR, "Gerar uma senha forte");
        gerar.setOnAction(e -> new DialogoGeradorSenha(dono).abrir()
                .ifPresent(oculto::setText));

        HBox linha = new HBox(8, pilha, ver, copiar, gerar);
        linha.setAlignment(Pos.CENTER_LEFT);

        VBox caixa = new VBox(6, Secao.campo(nome), linha);

        // Histórico: só aparece quando existe algo guardado.
        if (nota.getId() != null) {
            List<NotaDao.ValorAnterior> anteriores = servico.historicoDoCampo(nota.getId(), nome);
            if (!anteriores.isEmpty()) {
                Button historico = Botoes.link("Ver senhas anteriores (" + anteriores.size() + ")");
                historico.setOnAction(e -> mostrarHistorico(nome, anteriores));
                caixa.getChildren().add(historico);
            }
        }
        return caixa;
    }

    private void mostrarHistorico(String nome, List<NotaDao.ValorAnterior> anteriores) {
        VBox lista = new VBox(10);
        java.time.format.DateTimeFormatter formato =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

        for (NotaDao.ValorAnterior anterior : anteriores) {
            Label quando = new Label("Trocada em " + formato.format(anterior.trocadoEm()));
            quando.getStyleClass().add("secao-dica");

            TextField valor = new TextField(anterior.valor());
            valor.getStyleClass().add("campo");
            valor.setEditable(false);

            Button copiar = Botoes.icone(Icone.Simbolo.COPIAR, "Copiar " + nome.toLowerCase());
            copiar.setOnAction(e -> AreaTransferencia.copiarSegredo(anterior.valor()));

            HBox linha = new HBox(8, valor, copiar);
            HBox.setHgrow(valor, Priority.ALWAYS);

            lista.getChildren().add(new VBox(4, quando, linha));
        }

        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle("Valores anteriores de " + nome);
        dialogo.setHeaderText(null);
        dialogo.initOwner(dono);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ScrollPane rolagem = new ScrollPane(new VBox(10, lista));
        rolagem.setFitToWidth(true);
        rolagem.setPrefViewportWidth(420);
        rolagem.setPrefViewportHeight(Math.min(400, 110 * anteriores.size()));
        rolagem.getStyleClass().add("rolagem-formulario");

        VBox conteudo = new VBox(12,
                Secao.dica("Guardados automaticamente quando você trocou o valor."),
                rolagem);
        conteudo.setPadding(new javafx.geometry.Insets(18));

        dialogo.getDialogPane().setContent(conteudo);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        dialogo.showAndWait();
    }

    private void ajustarCorpoAoTipo() {
        TipoNota tipo = campoTipo.getValue() == null ? TipoNota.LIVRE : campoTipo.getValue();
        rotuloCorpo.setText(tipo.rotuloDoCorpo());

        boolean codigo = tipo == TipoNota.CODIGO;
        campoCorpo.getStyleClass().remove("campo-codigo");
        if (codigo) {
            campoCorpo.getStyleClass().add("campo-codigo");
        }
        campoCorpo.setPrefRowCount(tipo.corpoEPrincipal() ? 14 : 4);
        campoCorpo.setPromptText(switch (tipo) {
            case LIVRE -> "Escreva à vontade...";
            case CODIGO -> "Cole aqui o SQL, o script ou o comando.";
            case CREDENCIAL -> "Observações: ambiente, validade, quem liberou...";
            case LINK -> "Para que serve este endereço?";
        });
    }

    // ------------------------------------------------------ carregar/coletar

    private void carregarValores() {
        campoTitulo.setText(nota.getTitulo());
        campoTipo.setValue(nota.getTipo());
        campoCorpo.setText(nota.getCorpo());
        campoProtegida.setSelected(nota.isProtegida());

        if (nota.getCategoriaId() != null) {
            categorias.stream()
                    .filter(c -> c.getId().equals(nota.getCategoriaId()))
                    .findFirst()
                    .ifPresent(campoCategoria::setValue);
        }

        montarCamposDoTipo();
        preencherCamposDoTipo();
        ajustarCorpoAoTipo();
    }

    private void preencherCamposDoTipo() {
        camposComuns.forEach((nome, campo) ->
                campo.setText(nota.valor(nome).orElse("")));

        camposSegredo.forEach((nome, campo) -> {
            String valor = nota.valor(nome).orElse("");
            // Com o app trancado o valor vem como marcador, e não como o
            // segredo: não faz sentido colocá-lo no campo de edição.
            campo.setText(NotaDao.SEGREDO_OCULTO.equals(valor) ? "" : valor);
        });

        nota.valor("Linguagem").ifPresent(campoLinguagem::setValue);
    }

    /** Copia o que está na tela para o modelo, antes de remontar os campos. */
    private void recolherCamposDaTela() {
        camposComuns.forEach((nome, campo) ->
                nota.definirCampo(nome, campo.getText(), false));
        camposSegredo.forEach((nome, campo) ->
                nota.definirCampo(nome, campo.getText(), true));
        if (campoLinguagem.getValue() != null) {
            nota.definirCampo("Linguagem", campoLinguagem.getValue(), false);
        }
    }

    private Nota coletarValores() {
        nota.setTitulo(texto(campoTitulo.getText()));
        nota.setTipo(campoTipo.getValue());
        nota.setCorpo(campoCorpo.getText() == null ? "" : campoCorpo.getText());
        nota.setProtegida(campoProtegida.isSelected());
        nota.setCategoriaId(campoCategoria.getValue() == null
                ? null : campoCategoria.getValue().getId());

        // Mantém apenas os campos do tipo atual, mais os que o usuário criou.
        List<CampoNota> extras = new ArrayList<>(nota.camposExtras());
        nota.getCampos().clear();
        recolherCamposDaTela();
        for (CampoNota extra : extras) {
            nota.definirCampo(extra.getChave(), extra.getValor(), extra.isSensivel());
        }

        return nota;
    }

    // -------------------------------------------------------------- apoio

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String dicaDoCampo(String nome) {
        return switch (nome) {
            case "Usuário" -> "Ex.: admin";
            case "Endereço" -> "Ex.: portal.cliente.com.br";
            case "URL" -> "Ex.: https://sistema.exemplo.com.br";
            default -> "";
        };
    }

}
