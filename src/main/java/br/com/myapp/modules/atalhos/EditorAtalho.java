package br.com.myapp.modules.atalhos;

import br.com.myapp.core.Config;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Navegadores;
import br.com.myapp.ui.Secao;
import br.com.myapp.ui.SeletorNavegador;
import br.com.myapp.ui.SeletorSimbolo;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cadastro de um atalho.
 *
 * <pre>
 *   O QUE ABRE ────────────────────────────────────────────
 *   [Aplicativo | Pasta | Site | Arquivo]
 *   Caminho   [C:\...\Teams.lnk                ] [Procurar…]
 *   Nome      [Teams                             ]
 *   Grupo     [Aplicativos do dia a dia ▾]
 *   ÍCONE ─────────────────────────────────────────────────
 *   ┌────┐  [Do programa | Símbolo | Imagem]
 *   │ ▣  │  O mesmo ícone que aparece no Windows.
 *   └────┘
 * </pre>
 *
 * <p>Colar ou procurar o caminho já preenche o resto: o tipo é deduzido, o
 * nome sai do arquivo ou do site, e o ícone do programa aparece na prévia.
 * O que a pessoa digitar no nome vale mais que a sugestão.
 */
public class EditorAtalho {

    private final Window dono;
    private final Atalho atalho;
    private final boolean ehNovo;
    private final List<GrupoAtalho> grupos;

    private final ToggleGroup grupoTipo = new ToggleGroup();
    private final Map<TipoAtalho, ToggleButton> botoesTipo = new EnumMap<>(TipoAtalho.class);
    private final TextField campoAlvo = new TextField();
    private final Button procurar = Botoes.comum("Procurar…", Icone.Simbolo.PASTA);
    private final Label rotuloAlvo = new Label();
    private final TextField campoNome = new TextField();
    private final TextField campoArgumentos = new TextField();
    private final VBox blocoArgumentos;
    private final ComboBox<GrupoAtalho> campoGrupo = new ComboBox<>();
    private SeletorNavegador campoNavegador;
    private VBox blocoNavegador;

    private final ToggleGroup grupoModo = new ToggleGroup();
    private final ToggleButton modoAuto = new ToggleButton("Do programa");
    private final ToggleButton modoSimbolo = new ToggleButton("Símbolo");
    private final ToggleButton modoImagem = new ToggleButton("Imagem");
    private final StackPane previa = new StackPane();
    private final Label estadoIcone = new Label();
    private final SeletorSimbolo seletorSimbolo = new SeletorSimbolo();
    private final ColorPicker campoCor = new ColorPicker();
    private final VBox blocoSimbolo;
    private final Button escolherImagem = Botoes.comum("Escolher imagem…", Icone.Simbolo.PASTA);

    private final PauseTransition esperaDigitar = new PauseTransition(Duration.millis(600));
    /** A pessoa mexeu no nome: a sugestão não escreve mais por cima. */
    private boolean nomeEditado;
    private boolean preenchendo;
    private byte[] iconeAutomatico;
    private byte[] iconeImagem;
    private int busca;

    public EditorAtalho(Window dono, Atalho existente, List<GrupoAtalho> grupos, long grupoInicial) {
        this.dono = dono;
        this.ehNovo = existente == null;
        this.grupos = grupos;
        this.atalho = existente == null ? new Atalho() : existente;
        if (ehNovo) {
            atalho.setGrupoId(grupoInicial);
        }

        campoArgumentos.getStyleClass().add("campo");
        campoArgumentos.setPromptText("Opcional. Ex.: --profile \"Trabalho\"");
        blocoArgumentos = Secao.comRotulo("Argumentos", campoArgumentos);
        blocoArgumentos.getChildren().add(Secao.dica("O que vai depois do programa na linha de comando. "
                + "Quase nunca é preciso; deixe vazio se não souber."));

        campoCor.getStyleClass().add("campo");
        campoCor.setPrefWidth(150);
        HBox linhaCor = new HBox(8, new Label("Cor do fundo"), campoCor);
        linhaCor.setAlignment(Pos.CENTER_LEFT);
        blocoSimbolo = new VBox(10, seletorSimbolo, linhaCor);
    }

    /** Abre e devolve o atalho preenchido, ou vazio se cancelou. */
    public Optional<Atalho> abrir() {
        Dialog<Atalho> dialogo = new Dialog<>();
        dialogo.setTitle(ehNovo ? "Novo atalho" : "Editar atalho");
        dialogo.setHeaderText(null);
        if (dono != null) {
            dialogo.initOwner(dono);
        }
        dialogo.setResizable(true);

        ButtonType salvar = new ButtonType(ehNovo ? "Criar atalho" : "Salvar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, salvar);

        ScrollPane rolagem = Dialogos.rolavel(montar(), 600, 600);
        dialogo.getDialogPane().setContent(rolagem);
        Dialogos.aplicarTema(dialogo.getDialogPane());
        Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
        dialogo.getDialogPane().lookupButton(salvar).getStyleClass().add("botao-primario");
        dialogo.getDialogPane().lookupButton(salvar).disableProperty()
                .bind(campoAlvo.textProperty().isEmpty());

        carregar();
        Platform.runLater(campoAlvo::requestFocus);
        dialogo.setResultConverter(b -> b == salvar ? coletar() : null);
        return dialogo.showAndWait();
    }

    // ========================================================== formulário

    private VBox montar() {
        HBox seletorTipo = new HBox(0);
        seletorTipo.getStyleClass().add("seletor-visao");
        // Dentro de uma coluna, a pílula esticaria até a borda; fica do tamanho dos botões.
        seletorTipo.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        for (TipoAtalho t : TipoAtalho.values()) {
            ToggleButton b = new ToggleButton(t.rotulo);
            b.setGraphic(Icone.de(t.simbolo, 13));
            b.setToggleGroup(grupoTipo);
            b.setUserData(t);
            botoesTipo.put(t, b);
            seletorTipo.getChildren().add(b);
        }
        grupoTipo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true);
            } else {
                ajustarTipo();
            }
        });

        campoAlvo.getStyleClass().add("campo");
        HBox.setHgrow(campoAlvo, Priority.ALWAYS);
        campoAlvo.textProperty().addListener((obs, antes, agora) -> {
            if (preenchendo) {
                return;
            }
            // Colou um link ou um caminho: o tipo se ajusta sozinho.
            TipoAtalho deduzido = AtalhoService.deduzirTipo(agora);
            if (agora != null && !agora.isBlank() && deduzido != tipo()) {
                botoesTipo.get(deduzido).setSelected(true);
            }
            sugerirNome();
            esperaDigitar.playFromStart();
        });
        esperaDigitar.setOnFinished(e -> buscarIconeAutomatico());
        procurar.setOnAction(e -> procurar());
        HBox linhaAlvo = new HBox(8, campoAlvo, procurar);
        linhaAlvo.setAlignment(Pos.CENTER_LEFT);
        rotuloAlvo.getStyleClass().add("secao-campo");
        VBox blocoAlvo = new VBox(6, rotuloAlvo, linhaAlvo);

        campoNome.getStyleClass().addAll("campo", "campo-grande");
        campoNome.setPromptText("Como aparece no bloco");
        campoNome.textProperty().addListener((obs, antes, agora) -> {
            if (!preenchendo) {
                nomeEditado = agora != null && !agora.isBlank();
            }
        });

        campoGrupo.getItems().setAll(grupos);
        campoGrupo.getStyleClass().add("campo");
        campoGrupo.setConverter(new StringConverter<>() {
            @Override
            public String toString(GrupoAtalho g) {
                return g == null ? "" : g.getNome();
            }

            @Override
            public GrupoAtalho fromString(String s) {
                return null;
            }
        });

        montarNavegador();

        Secao oQueAbre = new Secao(Icone.Simbolo.ABRIR_FORA, "O QUE ABRE",
                seletorTipo, blocoAlvo,
                Secao.comRotulo("Nome", campoNome),
                blocoArgumentos,
                blocoNavegador,
                Secao.comRotulo("Grupo", campoGrupo));

        // ---------- ícone ----------
        HBox seletorModo = new HBox(0, modoAuto, modoSimbolo, modoImagem);
        seletorModo.getStyleClass().add("seletor-visao");
        seletorModo.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        for (ToggleButton b : List.of(modoAuto, modoSimbolo, modoImagem)) {
            b.setToggleGroup(grupoModo);
        }
        grupoModo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true);
            } else {
                ajustarModo();
            }
        });
        seletorSimbolo.grupo().selectedToggleProperty().addListener((o, a, n) -> desenharPrevia());
        campoCor.valueProperty().addListener((o, a, n) -> desenharPrevia());
        escolherImagem.setOnAction(e -> escolherImagem());

        estadoIcone.getStyleClass().add("texto-fraco");
        estadoIcone.setWrapText(true);
        estadoIcone.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        previa.getStyleClass().add("previa-icone-atalho");
        previa.setMinSize(72, 72);
        previa.setMaxSize(72, 72);
        VBox ladoModo = new VBox(8, seletorModo, estadoIcone, escolherImagem);
        HBox.setHgrow(ladoModo, Priority.ALWAYS);
        HBox linhaIcone = new HBox(16, previa, ladoModo);
        linhaIcone.setAlignment(Pos.CENTER_LEFT);

        Secao icone = new Secao(Icone.Simbolo.ESTRELA, "ÍCONE", linhaIcone, blocoSimbolo);

        VBox forma = new VBox(18, oQueAbre, icone);
        forma.getStyleClass().add("formulario");
        return forma;
    }


    /**
     * "Abrir com", só para site. A primeira opção segue a preferência geral do
     * MyApp (Configurações → Sistema) e diz qual é ela agora; escolher um
     * navegador aqui vale mais que a preferência.
     */
    private void montarNavegador() {
        String geral = Config.get().navegadorPadrao;
        String rotulo = geral == null || geral.isBlank()
                ? "Preferência do MyApp (padrão do Windows)"
                : "Preferência do MyApp (" + Navegadores.nomeDe(geral) + ")";
        campoNavegador = new SeletorNavegador(rotulo);
        blocoNavegador = Secao.comRotulo("Abrir com", campoNavegador);
        blocoNavegador.getChildren().add(Secao.dica("Para separar trabalho e pessoal, ou para o sistema "
                + "que só funciona direito em um navegador. A preferência geral fica em Configurações → Sistema."));
    }

    private void ajustarTipo() {
        TipoAtalho t = tipo();
        boolean site = t == TipoAtalho.SITE;
        rotuloAlvo.setText(site ? "Endereço" : "Caminho");
        campoAlvo.setPromptText(t.exemplo);
        procurar.setVisible(!site);
        procurar.setManaged(!site);
        mostrar(blocoArgumentos, t == TipoAtalho.APLICATIVO);
        mostrar(blocoNavegador, site);
        modoAuto.setText(site ? "Do site" : t == TipoAtalho.PASTA ? "Da pasta" : "Do programa");
        sugerirNome();
        if (!preenchendo) {
            esperaDigitar.playFromStart();
        }
        ajustarModo();
    }

    private void ajustarModo() {
        mostrar(blocoSimbolo, modoSimbolo.isSelected());
        mostrar(escolherImagem, modoImagem.isSelected());
        if (modoAuto.isSelected()) {
            estadoIcone.setText(iconeAutomatico != null
                    ? (tipo() == TipoAtalho.SITE ? "O ícone do site." : "O mesmo ícone que aparece no Windows.")
                    : "Sem ícone por enquanto — aparece quando o caminho existir. Até lá, o símbolo do tipo.");
        } else if (modoSimbolo.isSelected()) {
            estadoIcone.setText("Um símbolo do MyApp, na cor que você quiser.");
        } else {
            estadoIcone.setText(iconeImagem != null ? "A imagem escolhida." : "PNG, JPG, GIF ou BMP — ajustada ao tamanho do bloco.");
        }
        desenharPrevia();
    }

    // ============================================================= ações

    private void procurar() {
        File escolhido;
        if (tipo() == TipoAtalho.PASTA) {
            DirectoryChooser seletor = new DirectoryChooser();
            seletor.setTitle("Escolha a pasta");
            escolhido = seletor.showDialog(campoAlvo.getScene().getWindow());
        } else {
            FileChooser seletor = new FileChooser();
            seletor.setTitle(tipo() == TipoAtalho.APLICATIVO ? "Escolha o programa" : "Escolha o arquivo");
            if (tipo() == TipoAtalho.APLICATIVO) {
                seletor.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Programas e atalhos", "*.exe", "*.lnk", "*.bat", "*.cmd", "*.msc"),
                        new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
                File inicial = new File(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                        "Microsoft\\Windows\\Start Menu\\Programs");
                if (inicial.isDirectory()) {
                    seletor.setInitialDirectory(inicial);
                }
            }
            escolhido = seletor.showOpenDialog(campoAlvo.getScene().getWindow());
        }
        if (escolhido != null) {
            campoAlvo.setText(escolhido.getAbsolutePath());
            esperaDigitar.stop();
            buscarIconeAutomatico();
        }
    }

    private void sugerirNome() {
        if (nomeEditado || preenchendo) {
            return;
        }
        preenchendo = true;
        campoNome.setText(AtalhoService.nomeSugerido(campoAlvo.getText(), tipo()));
        preenchendo = false;
    }

    /** O ícone de verdade, buscado fora da tela; a prévia troca quando chega. */
    private void buscarIconeAutomatico() {
        String alvo = campoAlvo.getText();
        TipoAtalho tipo = tipo();
        int esta = ++busca;
        if (alvo == null || alvo.isBlank()) {
            iconeAutomatico = null;
            ajustarModo();
            return;
        }
        estadoIcone.setText("Buscando o ícone…");
        Thread t = new Thread(() -> {
            byte[] png = IconesAtalho.automatico(tipo, alvo).orElse(null);
            Platform.runLater(() -> {
                if (esta == busca) {
                    iconeAutomatico = png;
                    ajustarModo();
                }
            });
        }, "icone-atalho");
        t.setDaemon(true);
        t.start();
    }

    private void escolherImagem() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Escolha uma imagem para o atalho");
        seletor.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = seletor.showOpenDialog(campoAlvo.getScene().getWindow());
        if (f == null) {
            return;
        }
        try {
            iconeImagem = IconesAtalho.daImagem(f.toPath());
            ajustarModo();
        } catch (Exception e) {
            Dialogos.erro(campoAlvo.getScene().getWindow(), "Imagem não aceita", e.getMessage());
        }
    }

    private void desenharPrevia() {
        previa.getChildren().setAll(IconesAtalho.desenho(rascunho(), 48));
    }

    /** Um atalho com o que está na tela agora, para a prévia. */
    private Atalho rascunho() {
        Atalho r = new Atalho();
        r.setTipo(tipo());
        r.setModoIcone(modo());
        r.setIconePng(modo() == Atalho.ModoIcone.IMAGEM ? iconeImagem : iconeAutomatico);
        r.setIconeSimbolo(seletorSimbolo.chave());
        r.setCor(modo() == Atalho.ModoIcone.SIMBOLO ? paraHex(campoCor.getValue()) : null);
        return r;
    }

    // ===================================================== carregar/coletar

    private void carregar() {
        preenchendo = true;
        botoesTipo.get(atalho.getTipo()).setSelected(true);
        campoAlvo.setText(atalho.getAlvo());
        campoNome.setText(atalho.getNome());
        campoArgumentos.setText(atalho.getArgumentos());
        campoNavegador.selecionar(atalho.getNavegador());
        campoGrupo.setValue(grupos.stream().filter(g -> g.getId() == atalho.getGrupoId()).findFirst()
                .orElse(grupos.isEmpty() ? null : grupos.get(0)));
        nomeEditado = !atalho.getNome().isBlank();

        switch (atalho.getModoIcone()) {
            case AUTOMATICO -> {
                iconeAutomatico = atalho.getIconePng();
                modoAuto.setSelected(true);
            }
            case IMAGEM -> {
                iconeImagem = atalho.getIconePng();
                modoImagem.setSelected(true);
            }
            case SIMBOLO -> modoSimbolo.setSelected(true);
        }
        seletorSimbolo.selecionar(atalho.getIconeSimbolo() != null ? atalho.getIconeSimbolo() : "monitor");
        try {
            campoCor.setValue(Color.web(atalho.getCor() != null ? atalho.getCor() : IconesAtalho.corSegura(atalho)));
        } catch (Exception e) {
            campoCor.setValue(Color.web("#4A8CFF"));
        }
        preenchendo = false;
        ajustarTipo();
        esperaDigitar.stop();
    }

    private Atalho coletar() {
        atalho.setTipo(tipo());
        atalho.setAlvo(campoAlvo.getText());
        atalho.setNome(campoNome.getText());
        atalho.setArgumentos(tipo() == TipoAtalho.APLICATIVO ? campoArgumentos.getText() : "");
        atalho.setNavegador(tipo() == TipoAtalho.SITE ? campoNavegador.exe() : "");
        if (campoGrupo.getValue() != null) {
            atalho.setGrupoId(campoGrupo.getValue().getId());
        }
        atalho.setModoIcone(modo());
        atalho.setIconeSimbolo(seletorSimbolo.chave());
        atalho.setCor(modo() == Atalho.ModoIcone.SIMBOLO ? paraHex(campoCor.getValue()) : null);
        atalho.setIconePng(switch (modo()) {
            case AUTOMATICO -> iconeAutomatico;
            case IMAGEM -> iconeImagem;
            case SIMBOLO -> null;
        });
        return atalho;
    }

    // ============================================================= apoio

    private TipoAtalho tipo() {
        return grupoTipo.getSelectedToggle() == null
                ? TipoAtalho.APLICATIVO : (TipoAtalho) grupoTipo.getSelectedToggle().getUserData();
    }

    private Atalho.ModoIcone modo() {
        if (modoSimbolo.isSelected()) {
            return Atalho.ModoIcone.SIMBOLO;
        }
        return modoImagem.isSelected() ? Atalho.ModoIcone.IMAGEM : Atalho.ModoIcone.AUTOMATICO;
    }

    private static String paraHex(Color c) {
        if (c == null) {
            return "#4A8CFF";
        }
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private static void mostrar(javafx.scene.Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
