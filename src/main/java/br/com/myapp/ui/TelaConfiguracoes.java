package br.com.myapp.ui;

import br.com.myapp.backup.BackupService;
import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.security.SecurityService;
import javafx.geometry.Insets;
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
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Janela de configurações, separada em abas.
 *
 * Tudo o que muda o comportamento do aplicativo esta reunido aqui, em vez de
 * espalhado pelas telas. As mudanças são gravadas quando você confirma.
 */
public class TelaConfiguracoes {

    private final Window dono;
    private final Runnable aoTrocarTema;
    private final Config config = Config.get();

    // --- Aparência ---
    private final ComboBox<String> campoTema = new ComboBox<>();

    // --- Segurança ---
    private final Spinner<Integer> campoInatividade = new Spinner<>(0, 480, 15);
    private final CheckBox campoBloquearAoMinimizar = new CheckBox("Trancar também ao minimizar para a bandeja");

    // --- lembretes ---
    private final TextField campoArquivoSom = new TextField();
    private final Spinner<Integer> campoSnooze = new Spinner<>(1, 240, 10);
    private final Spinner<Integer> campoCatchUp = new Spinner<>(0, 60, 7);

    // --- sistema ---
    private final CheckBox campoIniciarComWindows = new CheckBox("Abrir junto com o Windows");
    private final CheckBox campoIniciarMinimizado = new CheckBox("Começar minimizado na bandeja");
    private final CheckBox campoFecharVaiParaBandeja = new CheckBox("O botão X minimiza para a bandeja em vez de sair");

    // --- backup ---
    private final CheckBox campoBackupAuto = new CheckBox("Gerar backup automático");
    private final TextField campoPastaNuvem = new TextField();
    private final Spinner<Integer> campoHorasBackup = new Spinner<>(1, 720, 24);
    private final Spinner<Integer> campoManterBackups = new Spinner<>(1, 200, 15);

    public TelaConfiguracoes(Window dono, Runnable aoTrocarTema) {
        this.dono = dono;
        this.aoTrocarTema = aoTrocarTema;
    }

    public void abrir() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Configurações");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);

        ButtonType salvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);

        TabPane abas = new TabPane(
                aba("Aparência", abaAparencia()),
                aba("Segurança", abaSeguranca()),
                aba("Lembretes", abaLembretes()),
                aba("Sistema", abaSistema()),
                aba("Backup", abaBackup()));
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abas.setPrefSize(600, 480);

        dialogo.getDialogPane().setContent(abas);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");

        carregar();

        Optional<ButtonType> escolha = dialogo.showAndWait();
        if (escolha.isPresent() && escolha.get() == salvar) {
            gravar();
        }
    }

    private Tab aba(String titulo, VBox conteudo) {
        conteudo.setSpacing(14);
        conteudo.setPadding(new Insets(20));
        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        return new Tab(titulo, rolagem);
    }

    // ------------------------------------------------------------ abas

    private VBox abaAparencia() {
        campoTema.getItems().addAll("escuro", "claro");
        // "escuro" e "claro" são o valor gravado na configuração e comparado
        // pelo Shell — continuam em minúsculas. Só o que aparece na tela ganha
        // a maiúscula.
        campoTema.setConverter(new StringConverter<>() {
            @Override
            public String toString(String tema) {
                return tema == null || tema.isEmpty()
                        ? ""
                        : Character.toUpperCase(tema.charAt(0)) + tema.substring(1);
            }

            @Override
            public String fromString(String texto) {
                return texto == null ? null : texto.toLowerCase(java.util.Locale.ROOT);
            }
        });
        campoTema.getStyleClass().add("campo");
        campoTema.setPrefWidth(200);

        return new VBox(
                rotulo("TEMA"),
                campoTema,
                explicacao("O tema claro é mais confortável em ambiente muito iluminado."));
    }

    private VBox abaSeguranca() {
        campoInatividade.getStyleClass().add("campo");
        campoInatividade.setEditable(true);
        campoInatividade.setPrefWidth(120);

        HBox linhaInatividade = new HBox(10,
                new Label("Trancar sozinho após"), campoInatividade, new Label("minutos sem uso"));
        linhaInatividade.setAlignment(Pos.CENTER_LEFT);

        Button trocarSenha = Botoes.comum("Trocar a senha mestra", Icone.Simbolo.CHAVE);
        trocarSenha.setOnAction(e -> abrirTrocaDeSenha());

        return new VBox(
                rotulo("BLOQUEIO AUTOMÁTICO"),
                linhaInatividade,
                explicacao("Use 0 para desligar o bloqueio automático. "
                        + "Qualquer tecla ou movimento de mouse na janela reinicia a contagem."),
                campoBloquearAoMinimizar,
                separador(),
                rotulo("SENHA MESTRA"),
                trocarSenha,
                explicacao("A troca de senha não reescreve os dados: apenas a proteção da chave é refeita. "
                        + "Por isso é instantânea, mesmo com muito conteúdo guardado."));
    }

    private VBox abaLembretes() {
        campoArquivoSom.getStyleClass().add("campo");
        campoArquivoSom.setPromptText("Som padrão do Windows");

        Button escolherSom = Botoes.comum("Escolher .wav", Icone.Simbolo.PASTA);
        escolherSom.setOnAction(e -> {
            FileChooser seletor = new FileChooser();
            seletor.setTitle("Escolha o som do alerta");
            seletor.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Arquivos de som", "*.wav"));
            File inicial = new File("C:\\Windows\\Media");
            if (inicial.isDirectory()) {
                seletor.setInitialDirectory(inicial);
            }
            File escolhido = seletor.showOpenDialog(dono);
            if (escolhido != null) {
                campoArquivoSom.setText(escolhido.getAbsolutePath());
            }
        });

        Button testarSom = Botoes.comum("Testar", Icone.Simbolo.SOM);
        testarSom.setOnAction(e -> {
            gravarSomTemporario();
            WindowsIntegracao.tocarAlerta();
        });

        HBox linhaSom = new HBox(8, campoArquivoSom, escolherSom, testarSom);
        linhaSom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(campoArquivoSom, javafx.scene.layout.Priority.ALWAYS);

        campoSnooze.getStyleClass().add("campo");
        campoSnooze.setEditable(true);
        campoSnooze.setPrefWidth(110);
        HBox linhaSnooze = new HBox(10,
                new Label("O botão Adiar empurra o aviso em"), campoSnooze, new Label("minutos"));
        linhaSnooze.setAlignment(Pos.CENTER_LEFT);

        campoCatchUp.getStyleClass().add("campo");
        campoCatchUp.setEditable(true);
        campoCatchUp.setPrefWidth(110);
        HBox linhaCatchUp = new HBox(10,
                new Label("Ao abrir, procurar avisos perdidos dos últimos"),
                campoCatchUp, new Label("dias"));
        linhaCatchUp.setAlignment(Pos.CENTER_LEFT);

        return new VBox(
                rotulo("SOM DO ALERTA"),
                linhaSom,
                explicacao("Este é o som usado por todos os lembretes. Se cada lembrete "
                        + "vai tocar ou ficar em silêncio é escolha dele, marcada no "
                        + "próprio cadastro."),
                separador(),
                rotulo("COMPORTAMENTO"),
                linhaSnooze,
                linhaCatchUp,
                explicacao("Com o computador desligado no horário de um lembrete, o aviso aparece "
                        + "na próxima abertura, marcado como atrasado. Use 0 para não recuperar nada."));
    }

    private VBox abaSistema() {
        return new VBox(
                rotulo("INICIALIZAÇÃO"),
                campoIniciarComWindows,
                campoIniciarMinimizado,
                explicacao("A inicialização automática só funciona com o aplicativo instalado. "
                        + "Rodando pelo Maven durante o desenvolvimento, a opção fica sem efeito."),
                separador(),
                rotulo("JANELA"),
                campoFecharVaiParaBandeja,
                explicacao("Com esta opção ligada, o aplicativo continua vigiando a agenda depois "
                        + "de você fechar a janela. Para encerrar de verdade, use Sair no menu da bandeja."));
    }

    private VBox abaBackup() {
        campoPastaNuvem.getStyleClass().add("campo");
        campoPastaNuvem.setPromptText("Ex.: C:\\Users\\Você\\OneDrive\\Backups\\MyApp");

        Button escolherPasta = Botoes.comum("Escolher pasta", Icone.Simbolo.PASTA);
        escolherPasta.setOnAction(e -> {
            DirectoryChooser seletor = new DirectoryChooser();
            seletor.setTitle("Pasta de destino dos backups");
            File escolhida = seletor.showDialog(dono);
            if (escolhida != null) {
                campoPastaNuvem.setText(escolhida.getAbsolutePath());
            }
        });

        HBox linhaPasta = new HBox(8, campoPastaNuvem, escolherPasta);
        linhaPasta.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(campoPastaNuvem, javafx.scene.layout.Priority.ALWAYS);

        campoHorasBackup.getStyleClass().add("campo");
        campoHorasBackup.setEditable(true);
        campoHorasBackup.setPrefWidth(110);

        campoManterBackups.getStyleClass().add("campo");
        campoManterBackups.setEditable(true);
        campoManterBackups.setPrefWidth(110);

        HBox linhaIntervalo = new HBox(10,
                new Label("A cada"), campoHorasBackup, new Label("horas, mantendo os últimos"),
                campoManterBackups);
        linhaIntervalo.setAlignment(Pos.CENTER_LEFT);

        Button exportarAgora = Botoes.primario("Exportar agora com senha própria", Icone.Simbolo.ENVIAR);
        exportarAgora.setOnAction(e -> exportarManual());

        Button restaurar = Botoes.perigo("Restaurar de um arquivo", Icone.Simbolo.BAIXAR);
        restaurar.setOnAction(e -> restaurarManual());

        HBox linhaBotoes = new HBox(10, exportarAgora, restaurar);

        return new VBox(
                rotulo("BACKUP AUTOMÁTICO"),
                campoBackupAuto,
                linhaPasta,
                linhaIntervalo,
                explicacao("O backup automático é cifrado com uma chave derivada desta máquina, para "
                        + "rodar sozinho. Protege contra perda de arquivo e leitura casual na nuvem. "
                        + "Para levar o backup para fora, use a exportação manual abaixo e escolha a senha."),
                separador(),
                rotulo("MANUAL"),
                linhaBotoes,
                explicacao("A restauração substitui o banco atual. O anterior é preservado ao lado, "
                        + "com o nome myapp.db.antes-da-restauracao."));
    }

    // ------------------------------------------------------ carregar/gravar

    private void carregar() {
        campoTema.setValue(config.tema);
        campoInatividade.getValueFactory().setValue(config.minutosParaBloquear);
        campoBloquearAoMinimizar.setSelected(config.bloquearAoMinimizar);

        campoArquivoSom.setText(config.somAlerta);
        campoSnooze.getValueFactory().setValue(config.minutosSnooze);
        campoCatchUp.getValueFactory().setValue(config.diasDeCatchUp);

        campoIniciarComWindows.setSelected(WindowsIntegracao.iniciaComWindows());
        campoIniciarMinimizado.setSelected(config.iniciarMinimizado);
        campoFecharVaiParaBandeja.setSelected(config.fecharVaiParaBandeja);

        campoBackupAuto.setSelected(config.backupAutomatico);
        campoPastaNuvem.setText(config.pastaBackupNuvem);
        campoHorasBackup.getValueFactory().setValue(config.horasEntreBackups);
        campoManterBackups.getValueFactory().setValue(config.backupsParaManter);
    }

    private void gravar() {
        boolean temaMudou = !campoTema.getValue().equals(config.tema);

        config.tema = campoTema.getValue();
        config.minutosParaBloquear = campoInatividade.getValue();
        config.bloquearAoMinimizar = campoBloquearAoMinimizar.isSelected();

        config.somAlerta = campoArquivoSom.getText() == null ? "" : campoArquivoSom.getText().trim();
        config.minutosSnooze = campoSnooze.getValue();
        config.diasDeCatchUp = campoCatchUp.getValue();

        config.iniciarMinimizado = campoIniciarMinimizado.isSelected();
        config.fecharVaiParaBandeja = campoFecharVaiParaBandeja.isSelected();

        config.backupAutomatico = campoBackupAuto.isSelected();
        config.pastaBackupNuvem = campoPastaNuvem.getText() == null ? "" : campoPastaNuvem.getText().trim();
        config.horasEntreBackups = campoHorasBackup.getValue();
        config.backupsParaManter = campoManterBackups.getValue();

        config.salvar();

        // A inicialização automática mexe no registro do Windows, não no config.json.
        if (campoIniciarComWindows.isSelected() != WindowsIntegracao.iniciaComWindows()) {
            boolean ok = WindowsIntegracao.definirIniciaComWindows(campoIniciarComWindows.isSelected());
            if (!ok && campoIniciarComWindows.isSelected()) {
                Dialogos.info(dono, "Inicialização automática",
                        "Não foi possível cadastrar a inicialização automática.\n\n"
                                + "Isso é esperado quando o aplicativo roda pelo Maven, sem instalação. "
                                + "Depois de gerar o instalador, a opção passa a funcionar.");
            }
        }

        if (temaMudou && aoTrocarTema != null) {
            aoTrocarTema.run();
        }
        Log.info("Configurações salvas.");
    }

    /** Aplica só o som, para o botao Testar funcionar antes de salvar. */
    private void gravarSomTemporario() {
        config.somAlerta = campoArquivoSom.getText() == null ? "" : campoArquivoSom.getText().trim();
    }

    // --------------------------------------------------------- senha/backup

    private void abrirTrocaDeSenha() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Trocar a senha mestra");
        dialogo.setHeaderText(null);
        dialogo.initOwner(dono);

        PasswordField atual = new PasswordField();
        atual.setPromptText("Senha atual");
        atual.getStyleClass().add("campo");

        PasswordField nova = new PasswordField();
        nova.setPromptText("Nova senha");
        nova.getStyleClass().add("campo");

        PasswordField confirma = new PasswordField();
        confirma.setPromptText("Repita a nova senha");
        confirma.getStyleClass().add("campo");

        TextField dica = new TextField();
        dica.setPromptText("Nova dica (opcional)");
        dica.getStyleClass().add("campo");

        Label erro = new Label();
        erro.getStyleClass().add("texto-perigo");
        erro.setWrapText(true);
        erro.setVisible(false);

        VBox conteudo = new VBox(12, atual, nova, confirma, dica, erro);
        conteudo.setPadding(new Insets(20));
        conteudo.setPrefWidth(380);

        ButtonType confirmar = new ButtonType("Trocar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirmar);
        dialogo.getDialogPane().setContent(conteudo);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), confirmar);

        // Valida sem fechar a janela quando algo estiver errado.
        Button botaoConfirmar = (Button) dialogo.getDialogPane().lookupButton(confirmar);
        botaoConfirmar.getStyleClass().add("botao-primario");
        botaoConfirmar.addEventFilter(javafx.event.ActionEvent.ACTION, evento -> {
            char[] senhaAtual = atual.getText().toCharArray();
            char[] senhaNova = nova.getText().toCharArray();
            char[] senhaConfirma = confirma.getText().toCharArray();
            try {
                if (!java.util.Arrays.equals(senhaNova, senhaConfirma)) {
                    erro.setText("As duas senhas novas não são iguais.");
                    erro.setVisible(true);
                    evento.consume();
                    return;
                }
                SecurityService.trocarSenha(senhaAtual, senhaNova,
                        dica.getText() == null || dica.getText().isBlank() ? null : dica.getText().trim());
                Aviso.sucesso("Senha mestra trocada com sucesso!");
            } catch (Exception e) {
                erro.setText(mensagemDe(e));
                erro.setVisible(true);
                evento.consume();
            } finally {
                java.util.Arrays.fill(senhaConfirma, '\0');
            }
        });

        dialogo.showAndWait();
    }

    private void exportarManual() {
        DirectoryChooser seletor = new DirectoryChooser();
        seletor.setTitle("Onde salvar o backup");
        File pasta = seletor.showDialog(dono);
        if (pasta == null) {
            return;
        }

        Optional<char[]> senha = pedirSenha("Senha do backup",
                "Escolha a senha que vai proteger este arquivo.\n"
                        + "Ela é independente da senha mestra e será pedida na restauração.");
        if (senha.isEmpty()) {
            return;
        }
        try {
            Path arquivo = BackupService.exportar(pasta.toPath(), senha.get());
            Dialogos.info(dono, "Backup gerado", "Arquivo salvo em:\n" + arquivo);
        } catch (Exception e) {
            Dialogos.erro(dono, "Falha no backup", mensagemDe(e));
        }
    }

    private void restaurarManual() {
        boolean confirmou = Dialogos.confirmar(dono, "Restaurar um backup?",
                "A restauração substitui todos os dados atuais pelos do arquivo. "
                        + "O banco de agora é preservado ao lado, "
                        + "como myapp.db.antes-da-restauracao.",
                "Restaurar");
        if (!confirmou) {
            return;
        }

        FileChooser seletor = new FileChooser();
        seletor.setTitle("Escolha o arquivo de backup");
        seletor.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Backup do MyApp", "*.myappbkp"));
        File arquivo = seletor.showOpenDialog(dono);
        if (arquivo == null) {
            return;
        }

        Optional<char[]> senha = pedirSenha("Senha do backup",
                "Digite a senha usada quando este backup foi gerado.");
        if (senha.isEmpty()) {
            return;
        }
        try {
            BackupService.restaurar(arquivo.toPath(), senha.get());
            Dialogos.info(dono, "Backup restaurado",
                    "Os dados foram restaurados.\n\nFeche e abra o MyApp para carregar tudo.");
        } catch (Exception e) {
            Dialogos.erro(dono, "Falha na restauração", mensagemDe(e));
        }
    }

    private Optional<char[]> pedirSenha(String titulo, String explicacao) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        dialogo.initOwner(dono);

        Label texto = new Label(explicacao);
        texto.setWrapText(true);
        texto.getStyleClass().add("subtitulo");

        PasswordField campo = new PasswordField();
        campo.setPromptText("Senha do arquivo");
        campo.getStyleClass().addAll("campo", "campo-grande");

        VBox conteudo = new VBox(14, texto, campo);
        conteudo.setPadding(new Insets(20));
        conteudo.setPrefWidth(400);

        ButtonType ok = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ok);
        dialogo.getDialogPane().setContent(conteudo);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        dialogo.getDialogPane().lookupButton(ok).getStyleClass().add("botao-primario");

        Optional<ButtonType> escolha = dialogo.showAndWait();
        if (escolha.isPresent() && escolha.get() == ok && !campo.getText().isEmpty()) {
            return Optional.of(campo.getText().toCharArray());
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------- apoio

    private Label rotulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("rotulo");
        return l;
    }

    private Label explicacao(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-fraco");
        l.setWrapText(true);
        l.setMaxWidth(520);
        return l;
    }

    private javafx.scene.layout.Region separador() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        r.getStyleClass().add("separador");
        r.setPrefHeight(1);
        VBox.setMargin(r, new Insets(8, 0, 8, 0));
        return r;
    }

    private String mensagemDe(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }
}
