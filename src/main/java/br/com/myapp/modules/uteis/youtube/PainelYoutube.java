package br.com.myapp.modules.uteis.youtube;

import br.com.myapp.core.Config;
import br.com.myapp.core.Log;
import br.com.myapp.core.WindowsIntegracao;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.CampoBusca;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.ListaOpcoes;
import br.com.myapp.ui.Opcao;
import br.com.myapp.ui.Secao;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Baixar vídeo ou só o áudio do YouTube, com o yt-dlp por baixo.
 *
 * <pre>
 *   ▶ BAIXAR DO YOUTUBE ─────────────────────── [↻ Atualizar yt-dlp]
 *   yt-dlp 2026.08.19 · FFmpeg · Deno — prontos
 *   [ link do vídeo                                ] [Colar]
 *   ┌───────┐ Título do vídeo
 *   │ capa  │ Canal · 3:45
 *   └───────┘
 *   [Vídeo | Só o áudio]   Qualidade [Melhor disponível ▾]
 *   Salvar em [C:\Users\...\Downloads              ] [Escolher] [▢]
 *   ╭ Baixar a playlist inteira               (●  ) ╮
 *   ╰ Guardar capa e dados no arquivo         (══●) ╯
 *   [⬇ Baixar]  ▓▓▓▓▓▓░░░░ Baixando o vídeo 45% · 3,2 MB/s · falta 0:12
 * </pre>
 *
 * <p>É o que se fazia no CMD ({@code yt-dlp link} e
 * {@code yt-dlp -x --audio-format mp3 link}), com as escolhas à vista. Ver
 * {@code docs/UTEIS.md}.
 */
public class PainelYoutube extends Secao {

    private static final List<Integer> ALTURAS = List.of(0, 2160, 1440, 1080, 720, 480, 360);

    private final Config config = Config.get();
    private final Baixador baixador = new Baixador();
    private final ExecutorService fundo = Executors.newSingleThreadExecutor(t -> {
        Thread th = new Thread(t, "uteis-youtube");
        th.setDaemon(true);
        return th;
    });

    // --- programas ---
    private final VBox areaFerramentas = new VBox(8);
    private final Button atualizar = Botoes.miudo("Atualizar yt-dlp", Icone.Simbolo.GERAR);
    private boolean ferramentasProntas;
    private volatile boolean instalacaoCancelada;

    // --- link e prévia ---
    private final CampoBusca campoLink = new CampoBusca("Cole aqui o link do vídeo (ou da playlist)", false);
    private final Label estadoLink = new Label();
    private final ImageView capa = new ImageView();
    private final Label tituloMidia = new Label();
    private final Label detalheMidia = new Label();
    private final HBox previa;
    private final PauseTransition esperaDigitar = new PauseTransition(Duration.millis(700));
    private int leitura;
    private Midia midia;

    // --- escolhas ---
    private final ToggleGroup grupoTipo = new ToggleGroup();
    private final ToggleButton botaoVideo = new ToggleButton("Vídeo");
    private final ToggleButton botaoAudio = new ToggleButton("Só o áudio");
    private final ComboBox<Integer> campoAltura = new ComboBox<>();
    private final ComboBox<PedidoDownload.FormatoAudio> campoFormato = new ComboBox<>();
    private final ComboBox<String> campoQualidadeAudio = new ComboBox<>();
    private final HBox linhaVideo;
    private final HBox linhaAudio;
    private final TextField campoPasta = new TextField();
    private final Opcao opcaoPlaylist = new Opcao(Icone.Simbolo.REPETIR, "Baixar a playlist inteira")
            .comDescricao("Quando o link é de um vídeo dentro de uma playlist, baixa a lista toda, "
                    + "numa pasta com o nome dela. Desligado, baixa só o vídeo do link.");
    private final Opcao opcaoEmbutir = new Opcao(Icone.Simbolo.ETIQUETA, "Guardar capa e dados no arquivo")
            .comDescricao("Título, canal e a capa do vídeo vão para dentro do arquivo — "
                    + "aparecem no player e no Explorador do Windows.");

    // --- download ---
    private final Button baixar = Botoes.primario("Baixar", Icone.Simbolo.BAIXAR);
    private final Button cancelar = Botoes.perigo("Cancelar", Icone.Simbolo.FECHAR);
    private final ProgressBar barra = new ProgressBar(0);
    private final Label estado = new Label();
    private final HBox resultado = new HBox(10);
    private int partes;
    private int itemAtual;

    public PainelYoutube() {
        super(Icone.Simbolo.RETOMAR, "BAIXAR DO YOUTUBE",
                "Cole o link de um vídeo e escolha se quer o vídeo ou só o áudio.");

        atualizar.setOnAction(e -> atualizarYtDlp());
        Botoes.instalarDica(atualizar, "O YouTube muda de tempos em tempos e o yt-dlp corre atrás. "
                + "Se um download começar a falhar, atualize.");
        comAcoes(atualizar);

        // ---------- link ----------
        HBox.setHgrow(campoLink, Priority.ALWAYS);
        campoLink.textProperty().addListener((obs, antes, agora) -> {
            midia = null;
            mostrarPrevia(false);
            atualizarBotaoBaixar();
            esperaDigitar.playFromStart();
        });
        // O Esc fica com o CampoBusca (limpa o link); o Enter lê na hora, sem
        // esperar a pausa da digitação.
        campoLink.aoApertarEnter(() -> {
            esperaDigitar.stop();
            lerLink();
        });
        esperaDigitar.setOnFinished(e -> lerLink());

        Button colar = Botoes.comum("Colar", Icone.Simbolo.COPIAR);
        colar.setOnAction(e -> {
            String copiado = Clipboard.getSystemClipboard().getString();
            if (copiado != null) {
                campoLink.setText(copiado.trim());
            }
        });
        HBox linhaLink = new HBox(8, campoLink, colar);
        linhaLink.setAlignment(Pos.CENTER_LEFT);

        estadoLink.getStyleClass().add("texto-fraco");
        estadoLink.setWrapText(true);
        estadoLink.setMinHeight(Region.USE_PREF_SIZE);
        mostrar(estadoLink, false);

        capa.setFitWidth(128);
        capa.setFitHeight(72);
        capa.setPreserveRatio(true);
        Rectangle canto = new Rectangle(128, 72);
        canto.setArcWidth(12);
        canto.setArcHeight(12);
        capa.setClip(canto);
        tituloMidia.getStyleClass().add("titulo-midia");
        tituloMidia.setWrapText(true);
        tituloMidia.setMinHeight(Region.USE_PREF_SIZE);
        detalheMidia.getStyleClass().add("texto-fraco");
        VBox textosMidia = new VBox(4, tituloMidia, detalheMidia);
        textosMidia.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textosMidia, Priority.ALWAYS);
        previa = new HBox(14, capa, textosMidia);
        previa.getStyleClass().add("previa-midia");
        previa.setAlignment(Pos.CENTER_LEFT);
        mostrarPrevia(false);

        // ---------- o que baixar ----------
        HBox seletorTipo = new HBox(0, botaoVideo, botaoAudio);
        seletorTipo.getStyleClass().add("seletor-visao");
        botaoVideo.setToggleGroup(grupoTipo);
        botaoAudio.setToggleGroup(grupoTipo);
        botaoVideo.setGraphic(Icone.de(Icone.Simbolo.RETOMAR, 13));
        botaoAudio.setGraphic(Icone.de(Icone.Simbolo.SOM, 13));
        // Num grupo, clicar no já escolhido desmarcaria os dois.
        grupoTipo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true);
            } else {
                ajustarTipo();
            }
        });

        campoAltura.getItems().setAll(ALTURAS);
        campoAltura.getStyleClass().add("campo");
        campoAltura.setConverter(conversor(this::nomeDaAltura));
        linhaVideo = new HBox(8, new Label("Qualidade"), campoAltura);
        linhaVideo.setAlignment(Pos.CENTER_LEFT);

        campoFormato.getItems().setAll(PedidoDownload.FormatoAudio.values());
        campoFormato.getStyleClass().add("campo");
        campoFormato.setConverter(conversor(f -> f == PedidoDownload.FormatoAudio.MP3
                ? "MP3 (toca em tudo)" : "M4A (menor, mesma qualidade)"));
        campoQualidadeAudio.getItems().setAll("0", "192K", "128K");
        campoQualidadeAudio.getStyleClass().add("campo");
        campoQualidadeAudio.setConverter(conversor(q -> switch (q) {
            case "192K" -> "192 kbps";
            case "128K" -> "128 kbps (menor)";
            default -> "Máxima";
        }));
        linhaAudio = new HBox(8, new Label("Formato"), campoFormato, new Label("Qualidade"), campoQualidadeAudio);
        linhaAudio.setAlignment(Pos.CENTER_LEFT);

        HBox linhaEscolha = new HBox(16, seletorTipo, linhaVideo, linhaAudio);
        linhaEscolha.setAlignment(Pos.CENTER_LEFT);

        // ---------- pasta ----------
        campoPasta.getStyleClass().add("campo");
        campoPasta.setEditable(false);
        HBox.setHgrow(campoPasta, Priority.ALWAYS);
        Button escolherPasta = Botoes.comum("Escolher pasta", Icone.Simbolo.PASTA);
        escolherPasta.setOnAction(e -> escolherPasta());
        Button abrirPasta = Botoes.icone(Icone.Simbolo.ABRIR_FORA, "Abrir a pasta no Explorador");
        abrirPasta.setOnAction(e -> {
            Path p = pasta();
            try {
                Files.createDirectories(p);
            } catch (Exception ignorado) {
                // o Explorador mostra o erro
            }
            WindowsIntegracao.abrir(p);
        });
        HBox linhaPasta = new HBox(8, campoPasta, escolherPasta, abrirPasta);
        linhaPasta.setAlignment(Pos.CENTER_LEFT);

        opcaoPlaylist.selectedProperty().addListener((obs, antes, agora) -> {
            if (PedidoDownload.pareceLink(campoLink.getText())) {
                lerLink();
            }
        });

        // ---------- baixar ----------
        baixar.setOnAction(e -> iniciarDownload());
        cancelar.setOnAction(e -> baixador.cancelar());
        barra.getStyleClass().add("barra-progresso");
        barra.setPrefWidth(220);
        estado.getStyleClass().add("texto-fraco");
        HBox linhaBaixar = new HBox(12, baixar, cancelar, barra, estado);
        linhaBaixar.setAlignment(Pos.CENTER_LEFT);
        resultado.setAlignment(Pos.CENTER_LEFT);
        resultado.getStyleClass().add("resultado-download");
        mostrar(resultado, false);
        emAndamento(false);

        com(areaFerramentas,
                Secao.comRotulo("Link", linhaLink), estadoLink, previa,
                Secao.comRotulo("O que baixar", linhaEscolha),
                Secao.comRotulo("Salvar em", linhaPasta),
                new ListaOpcoes(opcaoPlaylist, opcaoEmbutir),
                linhaBaixar, resultado);

        carregarPreferencias();
        conferirFerramentas();
    }

    // ============================================================ programas

    /** Procura os programas fora da tela e redesenha o quadro de cima. */
    private void conferirFerramentas() {
        areaFerramentas.getChildren().setAll(textoFraco("Conferindo o yt-dlp…"));
        fundo.submit(() -> {
            List<Ferramenta> faltam = Ferramentas.faltando();
            String versao = faltam.contains(Ferramenta.YT_DLP) ? "" : Ferramentas.versaoYtDlp().orElse("");
            boolean doMyApp = Ferramentas.localizar(Ferramenta.YT_DLP)
                    .map(Ferramentas.Local::doMyApp).orElse(false);
            Platform.runLater(() -> desenharFerramentas(faltam, versao, doMyApp));
        });
    }

    private void desenharFerramentas(List<Ferramenta> faltam, String versao, boolean doMyApp) {
        ferramentasProntas = faltam.isEmpty();
        atualizar.setDisable(faltam.contains(Ferramenta.YT_DLP));
        atualizarBotaoBaixar();

        if (ferramentasProntas) {
            Label pronto = textoFraco("yt-dlp " + versao + " · FFmpeg · Deno — prontos"
                    + (doMyApp ? " (instalados pelo MyApp)" : " (instalados no Windows)"));
            pronto.setGraphic(Icone.de(Icone.Simbolo.SUCESSO, 14, "icone-sucesso"));
            areaFerramentas.getChildren().setAll(pronto);
            return;
        }

        int mb = faltam.stream().mapToInt(f -> f.tamanhoMb).sum();
        Label titulo = new Label(faltam.size() == Ferramenta.values().length
                ? "Para baixar, o MyApp precisa de três programas gratuitos."
                : "Falta " + (faltam.size() == 1 ? "um programa" : faltam.size() + " programas") + " para baixar.");
        titulo.getStyleClass().add("titulo-aviso-ferramentas");
        titulo.setGraphic(Icone.de(Icone.Simbolo.ATENCAO, 15, "icone-atencao"));

        VBox lista = new VBox(4);
        for (Ferramenta f : faltam) {
            Label l = textoFraco("• " + f.nome + " — " + f.papel);
            lista.getChildren().add(l);
        }

        Button instalar = Botoes.primario("Instalar (cerca de " + mb + " MB)", Icone.Simbolo.BAIXAR);
        Button parar = Botoes.comum("Cancelar", Icone.Simbolo.FECHAR);
        ProgressBar progresso = new ProgressBar(0);
        progresso.getStyleClass().add("barra-progresso");
        progresso.setPrefWidth(200);
        Label andamento = textoFraco("");
        mostrar(parar, false);
        mostrar(progresso, false);
        instalar.setOnAction(e -> instalar(faltam, instalar, parar, progresso, andamento));
        parar.setOnAction(e -> instalacaoCancelada = true);

        HBox acoes = new HBox(10, instalar, parar, progresso, andamento);
        acoes.setAlignment(Pos.CENTER_LEFT);

        VBox caixa = new VBox(8, titulo, lista, acoes,
                Secao.dica("Vêm das páginas oficiais de cada projeto, no GitHub, e são conferidos "
                        + "antes de instalar. Ficam só para o MyApp, sem mexer no Windows."));
        caixa.getStyleClass().add("aviso-ferramentas");
        areaFerramentas.getChildren().setAll(caixa);
    }

    private void instalar(List<Ferramenta> faltam, Button instalar, Button parar,
                          ProgressBar progresso, Label andamento) {
        instalacaoCancelada = false;
        instalar.setDisable(true);
        mostrar(parar, true);
        mostrar(progresso, true);
        fundo.submit(() -> {
            try {
                for (Ferramenta f : faltam) {
                    Ferramentas.instalar(f, (ferramenta, baixado, total) -> Platform.runLater(() -> {
                        progresso.setProgress(total > 0 ? (double) baixado / total : -1);
                        andamento.setText("Baixando " + ferramenta.nome + "… " + mb(baixado)
                                + (total > 0 ? " de " + mb(total) : ""));
                    }), () -> instalacaoCancelada);
                }
                Platform.runLater(() -> Aviso.sucesso("Pronto! O baixador está instalado."));
            } catch (InterruptedException e) {
                Platform.runLater(() -> Aviso.info("Instalação cancelada."));
            } catch (Exception e) {
                Log.erro("Falha ao instalar as ferramentas do baixador", e);
                Platform.runLater(() -> Aviso.erro("Não deu para instalar: " + e.getMessage()));
            }
            Platform.runLater(this::conferirFerramentas);
        });
    }

    private void atualizarYtDlp() {
        atualizar.setDisable(true);
        Aviso.info("Procurando uma versão nova do yt-dlp…");
        fundo.submit(() -> {
            try {
                String mensagem = Ferramentas.atualizarYtDlp();
                Platform.runLater(() -> Aviso.sucesso(mensagem));
            } catch (Exception e) {
                Platform.runLater(() -> Aviso.erro("Não deu para atualizar: " + e.getMessage()));
            }
            Platform.runLater(this::conferirFerramentas);
        });
    }

    // ================================================================ link

    private void lerLink() {
        String link = campoLink.getText();
        if (!PedidoDownload.pareceLink(link)) {
            mostrar(estadoLink, link != null && !link.isBlank());
            estadoLink.setText("Isso não parece um link. Ele começa com https://");
            return;
        }
        if (!ferramentasProntas) {
            return;
        }
        int esta = ++leitura;
        boolean playlist = opcaoPlaylist.isSelected();
        estadoLink.setText("Lendo o link…");
        estadoLink.getStyleClass().remove("texto-perigo");
        mostrar(estadoLink, true);
        fundo.submit(() -> {
            try {
                Midia lida = baixador.informacoes(link, playlist);
                Platform.runLater(() -> {
                    if (esta == leitura) {
                        mostrarMidia(lida);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (esta == leitura) {
                        estadoLink.setText(e.getMessage());
                        estadoLink.getStyleClass().add("texto-perigo");
                    }
                });
            }
        });
    }

    private void mostrarMidia(Midia m) {
        midia = m;
        mostrar(estadoLink, false);
        tituloMidia.setText(m.titulo());
        String detalhe = m.playlist()
                ? "Playlist · " + m.itens() + (m.itens() == 1 ? " vídeo" : " vídeos")
                : String.join(" · ", List.of(m.canal(), m.duracaoFormatada()).stream()
                        .filter(s -> !s.isBlank()).toList());
        detalheMidia.setText(detalhe);
        capa.setImage(m.capa().isBlank() ? null : new Image(m.capa(), 256, 144, true, true, true));
        mostrarPrevia(true);

        // Só as resoluções que o vídeo tem, mais a "melhor disponível".
        Integer escolhida = campoAltura.getValue();
        int maior = m.maiorAltura();
        campoAltura.getItems().setAll(ALTURAS.stream()
                .filter(a -> a == 0 || maior == 0 || a <= maior).toList());
        // Passa por nulo para o "Melhor disponível (1080p)" ser redesenhado
        // mesmo quando a escolha não mudou.
        campoAltura.setValue(null);
        campoAltura.setValue(campoAltura.getItems().contains(escolhida) ? escolhida : 0);
        atualizarBotaoBaixar();
    }

    private String nomeDaAltura(Integer altura) {
        if (altura == null || altura == 0) {
            return midia != null && midia.maiorAltura() > 0
                    ? "Melhor disponível (" + midia.maiorAltura() + "p)" : "Melhor disponível";
        }
        return altura + "p" + (altura >= 2160 ? " (4K)" : altura == 1440 ? " (2K)" : "");
    }

    // ============================================================ baixar

    private void iniciarDownload() {
        PedidoDownload pedido = new PedidoDownload(
                campoLink.getText(),
                botaoAudio.isSelected() ? PedidoDownload.Tipo.AUDIO : PedidoDownload.Tipo.VIDEO,
                campoAltura.getValue() == null ? 0 : campoAltura.getValue(),
                campoFormato.getValue(), campoQualidadeAudio.getValue(),
                opcaoPlaylist.isSelected(), opcaoEmbutir.isSelected(), pasta());
        gravarPreferencias();

        partes = 0;
        itemAtual = -1;
        mostrar(resultado, false);
        emAndamento(true);
        barra.setProgress(-1);
        estado.setText("Começando…");

        fundo.submit(() -> {
            try {
                List<Path> prontos = baixador.baixar(pedido,
                        evento -> Platform.runLater(() -> mostrarEvento(pedido, evento)));
                Platform.runLater(() -> concluir(pedido, prontos));
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    emAndamento(false);
                    Aviso.info("Download cancelado. O que estava pela metade foi apagado.");
                });
            } catch (Exception e) {
                Log.aviso("Download falhou: " + e.getMessage());
                Platform.runLater(() -> {
                    emAndamento(false);
                    Aviso.erro("O download não deu certo.");
                    mostrarResultado(textoErro(e.getMessage()));
                });
            }
        });
    }

    private void mostrarEvento(PedidoDownload pedido, EventoDownload e) {
        switch (e.tipo()) {
            case BAIXANDO -> {
                if (e.item() > 0 && e.item() != itemAtual) {
                    itemAtual = e.item();
                    partes = 0;
                }
                barra.setProgress(e.fracao());
                StringBuilder s = new StringBuilder();
                if (e.itens() > 1) {
                    s.append("Vídeo ").append(e.item()).append(" de ").append(e.itens()).append(" · ");
                }
                s.append(pedido.tipo() == PedidoDownload.Tipo.VIDEO && partes == 0
                        ? "Baixando o vídeo" : "Baixando o áudio");
                if (e.fracao() >= 0) {
                    s.append(" ").append(Math.round(e.fracao() * 100)).append("%");
                }
                if (e.velocidade() > 0) {
                    s.append(" · ").append(mb((long) e.velocidade())).append("/s");
                }
                if (e.faltaSegundos() > 0) {
                    s.append(" · falta ").append(tempo(e.faltaSegundos()));
                }
                estado.setText(s.toString());
            }
            case PARTE_PRONTA -> partes++;
            case ETAPA -> {
                barra.setProgress(-1);
                estado.setText(EventoDownload.descreverEtapa(e.texto()));
            }
            default -> {
                // ARQUIVO e ERRO são tratados ao terminar.
            }
        }
    }

    private void concluir(PedidoDownload pedido, List<Path> prontos) {
        emAndamento(false);
        if (prontos.size() == 1) {
            Path arquivo = prontos.get(0);
            Aviso.sucesso("Download concluído!");
            Label nome = new Label(arquivo.getFileName().toString());
            nome.getStyleClass().add("nome-arquivo");
            nome.setGraphic(Icone.de(Icone.Simbolo.SUCESSO, 15, "icone-sucesso"));
            HBox.setHgrow(nome, Priority.ALWAYS);
            nome.setMaxWidth(Double.MAX_VALUE);
            Button abrir = Botoes.comum("Abrir", Icone.Simbolo.RETOMAR);
            abrir.setOnAction(ev -> abrirArquivo(arquivo, false));
            Button naPasta = Botoes.comum("Mostrar na pasta", Icone.Simbolo.PASTA);
            naPasta.setOnAction(ev -> abrirArquivo(arquivo, true));
            mostrarResultado(nome, abrir, naPasta);
        } else {
            Aviso.sucesso(prontos.size() + " arquivos baixados!");
            Label nome = new Label(prontos.size() + " arquivos em " + prontos.get(0).getParent());
            nome.getStyleClass().add("nome-arquivo");
            nome.setGraphic(Icone.de(Icone.Simbolo.SUCESSO, 15, "icone-sucesso"));
            HBox.setHgrow(nome, Priority.ALWAYS);
            nome.setMaxWidth(Double.MAX_VALUE);
            Button abrirPasta = Botoes.comum("Abrir a pasta", Icone.Simbolo.PASTA);
            abrirPasta.setOnAction(ev -> WindowsIntegracao.abrir(prontos.get(0).getParent()));
            mostrarResultado(nome, abrirPasta);
        }
    }

    /**
     * Abre o arquivo baixado, ou mostra na pasta. Se ele foi renomeado ou
     * movido depois do download, avisa e abre a pasta onde estava — em vez
     * de deixar o Explorador cair em Documentos sem explicação.
     */
    private void abrirArquivo(Path arquivo, boolean naPasta) {
        if (!Files.exists(arquivo)) {
            Aviso.atencao("Não achei " + arquivo.getFileName() + ". Ele foi renomeado ou movido? "
                    + "Abrindo a pasta onde ele estava.");
            if (arquivo.getParent() != null && Files.isDirectory(arquivo.getParent())) {
                WindowsIntegracao.abrir(arquivo.getParent());
            }
            return;
        }
        if (naPasta) {
            WindowsIntegracao.mostrarNaPasta(arquivo);
        } else {
            WindowsIntegracao.abrir(arquivo);
        }
    }

    // ============================================================ pasta

    /** A pasta fixa escolhida; sem escolha, a Downloads do Windows. */
    private Path pasta() {
        String escolhida = config.pastaDownloads;
        if (escolhida != null && !escolhida.isBlank()) {
            return Paths.get(escolhida);
        }
        return Paths.get(System.getProperty("user.home"), "Downloads");
    }

    private void escolherPasta() {
        DirectoryChooser seletor = new DirectoryChooser();
        seletor.setTitle("Pasta fixa dos vídeos e áudios");
        File atual = pasta().toFile();
        if (atual.isDirectory()) {
            seletor.setInitialDirectory(atual);
        }
        File escolhida = seletor.showDialog(getScene().getWindow());
        if (escolhida != null) {
            config.pastaDownloads = escolhida.getAbsolutePath();
            config.salvar();
            campoPasta.setText(escolhida.getAbsolutePath());
            Aviso.sucesso("Os downloads vão para " + escolhida.getName() + " daqui em diante.");
        }
    }

    // ====================================================== preferências

    private void carregarPreferencias() {
        ("AUDIO".equals(config.ytTipo) ? botaoAudio : botaoVideo).setSelected(true);
        campoAltura.setValue(ALTURAS.contains(config.ytAlturaVideo) ? config.ytAlturaVideo : 0);
        try {
            campoFormato.setValue(PedidoDownload.FormatoAudio.valueOf(config.ytFormatoAudio));
        } catch (Exception e) {
            campoFormato.setValue(PedidoDownload.FormatoAudio.MP3);
        }
        campoQualidadeAudio.setValue(campoQualidadeAudio.getItems().contains(config.ytQualidadeAudio)
                ? config.ytQualidadeAudio : "0");
        opcaoPlaylist.setSelected(config.ytPlaylist);
        opcaoEmbutir.setSelected(config.ytEmbutir);
        campoPasta.setText(pasta().toString());
        ajustarTipo();
    }

    /** As escolhas do último download voltam na próxima abertura. */
    private void gravarPreferencias() {
        config.ytTipo = botaoAudio.isSelected() ? "AUDIO" : "VIDEO";
        config.ytAlturaVideo = campoAltura.getValue() == null ? 0 : campoAltura.getValue();
        config.ytFormatoAudio = campoFormato.getValue().name();
        config.ytQualidadeAudio = campoQualidadeAudio.getValue();
        config.ytPlaylist = opcaoPlaylist.isSelected();
        config.ytEmbutir = opcaoEmbutir.isSelected();
        config.salvar();
    }

    // ============================================================ apoio

    private void ajustarTipo() {
        boolean audio = botaoAudio.isSelected();
        mostrar(linhaVideo, !audio);
        mostrar(linhaAudio, audio);
        baixar.setText(audio ? "Baixar o áudio" : "Baixar o vídeo");
    }

    private void emAndamento(boolean sim) {
        mostrar(cancelar, sim);
        mostrar(barra, sim);
        mostrar(estado, sim);
        campoLink.setDisable(sim);
        atualizar.setDisable(sim || !ferramentasProntas);
        atualizarBotaoBaixar();
    }

    private void atualizarBotaoBaixar() {
        baixar.setDisable(!ferramentasProntas || baixador.emAndamento()
                || cancelar.isVisible() || !PedidoDownload.pareceLink(campoLink.getText()));
    }

    private void mostrarPrevia(boolean visivel) {
        mostrar(previa, visivel);
    }

    private void mostrarResultado(javafx.scene.Node... nos) {
        resultado.getChildren().setAll(nos);
        mostrar(resultado, true);
    }

    private Label textoErro(String mensagem) {
        Label l = new Label(mensagem);
        l.getStyleClass().add("texto-perigo");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setGraphic(Icone.de(Icone.Simbolo.ERRO, 15, "icone-perigo"));
        HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static Label textoFraco(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-fraco");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static void mostrar(javafx.scene.Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }

    /** "3,2 MB", com vírgula. */
    static String mb(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024));
    }

    /** "0:12", "3:05", "1:02:07". */
    static String tempo(long segundos) {
        long h = segundos / 3600;
        long m = (segundos % 3600) / 60;
        long s = segundos % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static <T> StringConverter<T> conversor(java.util.function.Function<T, String> nome) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? "" : nome.apply(valor);
            }

            @Override
            public T fromString(String texto) {
                return null;
            }
        };
    }
}
