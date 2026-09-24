package br.com.myapp.modules.notas;

import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

/**
 * Criação e edição de uma categoria.
 *
 * O ícone é escolhido de uma lista curta, e não digitado: pedir que alguém
 * cole um emoji é pior do que oferecer os que fazem sentido aqui. A lista sai
 * do catálogo vetorial do aplicativo ({@code Icone.ESCOLHAS}), e o que se
 * grava no banco é a chave do ícone, não o desenho.
 */
public class DialogoCategoria {

    private final Window dono;
    private final Categoria categoria;
    private final boolean ehNova;
    private final NotaService servico;

    private final TextField campoNome = new TextField();
    private final ColorPicker campoCor = new ColorPicker();
    private final CheckBox campoExigeDesbloqueio =
            new CheckBox("Esconder esta categoria com o aplicativo trancado");

    private final ToggleGroup grupoIcones = new ToggleGroup();

    public DialogoCategoria(Window dono, Categoria existente, NotaService servico) {
        this.dono = dono;
        this.ehNova = existente == null;
        this.servico = servico;
        this.categoria = existente == null ? new Categoria() : existente;
    }

    public void abrir() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(ehNova ? "Nova categoria" : "Editar categoria");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }

        ButtonType salvar = new ButtonType(ehNova ? "Criar" : "Salvar",
                ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);
        dialogo.getDialogPane().setContent(montar());

        Dialogos.aplicarTema(dialogo.getDialogPane());

        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        Button botaoSalvar = (Button) dialogo.getDialogPane().lookupButton(salvar);
        botaoSalvar.getStyleClass().add("botao-primario");

        // Valida sem fechar a janela quando algo estiver errado.
        botaoSalvar.addEventFilter(javafx.event.ActionEvent.ACTION, evento -> {
            try {
                coletar();
                servico.salvarCategoria(categoria);
                Aviso.sucesso(ehNova
                        ? "Categoria criada com sucesso!"
                        : "Categoria atualizada.");
            } catch (IllegalArgumentException e) {
                Dialogos.erro(dono, "Não foi possível salvar", e.getMessage());
                evento.consume();
            }
        });

        carregar();
        dialogo.showAndWait();
    }

    private VBox montar() {
        campoNome.setPromptText("Ex.: Clientes");
        campoNome.getStyleClass().addAll("campo", "campo-grande");

        campoCor.getStyleClass().add("campo");
        campoCor.setPrefWidth(150);

        FlowPane icones = new FlowPane(8, 8);
        Icone.ESCOLHAS.forEach((chave, simbolo) -> {
            ToggleButton botao = new ToggleButton();
            botao.getStyleClass().add("chip-icone");
            botao.setGraphic(Icone.de(simbolo, 20));
            botao.setToggleGroup(grupoIcones);
            botao.setUserData(chave);
            icones.getChildren().add(botao);
        });

        Secao identificacao = new Secao(Icone.Simbolo.PASTA, "A CATEGORIA",
                Secao.comRotulo("Nome", campoNome),
                Secao.comRotulo("Ícone", icones),
                Secao.comRotulo("Cor", campoCor));

        Secao seguranca = new Secao(Icone.Simbolo.ESCUDO, "SEGURANÇA",
                campoExigeDesbloqueio,
                Secao.dica("Com isto marcado, nem os títulos das notas desta categoria "
                        + "aparecem enquanto o aplicativo estiver trancado."));

        VBox forma = new VBox(18, identificacao, seguranca);

        if (!ehNova) {
            Button excluir = new Button("Excluir categoria");
            excluir.getStyleClass().add("botao-perigo");
            excluir.setGraphic(Icone.de(Icone.Simbolo.EXCLUIR, 16));
            excluir.setOnAction(e -> confirmarExclusao());

            HBox linha = new HBox(excluir);
            linha.setAlignment(Pos.CENTER_LEFT);

            forma.getChildren().add(new Secao(Icone.Simbolo.ATENCAO, "EXCLUIR",
                    linha,
                    Secao.dica("As notas de dentro não são excluídas: elas ficam sem "
                            + "categoria e continuam acessíveis.")));
        }

        forma.getStyleClass().add("formulario");
        forma.setPadding(new Insets(18));
        forma.setPrefWidth(480);
        return forma;
    }

    private void confirmarExclusao() {
        boolean confirmou = Dialogos.confirmarExclusao(dono,
                "a categoria \"" + categoria.getNome() + "\"",
                "As notas de dentro não serão excluídas — ficarão sem categoria.");
        if (confirmou) {
            servico.excluirCategoria(categoria.getId());
            Aviso.sucesso("Categoria excluída.");
            // Fecha a janela: a categoria editada não existe mais.
            ((javafx.stage.Stage) campoNome.getScene().getWindow()).close();
        }
    }

    private void carregar() {
        campoNome.setText(categoria.getNome());
        campoExigeDesbloqueio.setSelected(categoria.isExigeDesbloqueio());

        try {
            campoCor.setValue(Color.web(categoria.getCor()));
        } catch (Exception e) {
            campoCor.setValue(Color.web("#4C8DFF"));
        }

        // Categorias criadas antes do catálogo vetorial guardaram um emoji.
        // Traduzindo pelo mesmo caminho que a tela usa para desenhar, a
        // seleção cai no ícone certo em vez de voltar para a pasta padrão.
        Icone.Simbolo atual = Icone.porChave(categoria.getIcone());
        grupoIcones.getToggles().stream()
                .filter(t -> Icone.ESCOLHAS.get(String.valueOf(t.getUserData())) == atual)
                .findFirst()
                .ifPresentOrElse(
                        t -> t.setSelected(true),
                        () -> grupoIcones.getToggles().get(0).setSelected(true));
    }

    private void coletar() {
        categoria.setNome(campoNome.getText() == null ? "" : campoNome.getText().trim());
        categoria.setCor(paraHex(campoCor.getValue()));
        categoria.setExigeDesbloqueio(campoExigeDesbloqueio.isSelected());

        if (grupoIcones.getSelectedToggle() != null) {
            categoria.setIcone((String) grupoIcones.getSelectedToggle().getUserData());
        }
    }

    private String paraHex(Color cor) {
        if (cor == null) {
            return "#4C8DFF";
        }
        return String.format("#%02X%02X%02X",
                (int) Math.round(cor.getRed() * 255),
                (int) Math.round(cor.getGreen() * 255),
                (int) Math.round(cor.getBlue() * 255));
    }
}
