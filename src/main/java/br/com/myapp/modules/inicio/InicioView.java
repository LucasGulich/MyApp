package br.com.myapp.modules.inicio;

import br.com.myapp.core.EventBus;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Arrastavel;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * A tela que abre depois da senha.
 *
 * Responde a uma pergunta só: <b>o que eu tenho para hoje?</b> Em cima, a
 * agenda do dia montada a partir dos lembretes; embaixo, os recados — os
 * papéis adesivos que você cola aqui e arrasta para onde quiser.
 *
 * Nada aqui é criado do zero: a agenda é uma leitura dos lembretes que já
 * existem, com o mesmo cálculo que o agendador usa para disparar os avisos.
 */
public class InicioView extends BorderPane {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final InicioService servico = new InicioService();
    private final LembreteService lembretes = new LembreteService();

    private final VBox listaHoje = new VBox(8);
    private final FlowPane muralRecados = new FlowPane(14, 14);

    /**
     * Onde o mural entra, ou o aviso de mural vazio no lugar dele.
     *
     * O aviso não pode ir dentro do {@code FlowPane}: lá ele recebe só a
     * largura preferida e fica encostado à esquerda — era o que acontecia.
     */
    private final VBox areaMural = new VBox();
    private final Label saudacao = new Label();
    private final Label resumoDoDia = new Label();

    public InicioView() {
        getStyleClass().add("conteudo");

        VBox conteudo = new VBox(22,
                montarCabecalho(),
                montarPainelHoje(),
                montarMural());
        conteudo.setPadding(new Insets(0, 8, 24, 0));

        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("rolagem-formulario");
        setCenter(rolagem);

        EventBus.ouvir(InicioService.RecadosMudaram.class,
                e -> Platform.runLater(this::recarregarRecados));
        EventBus.ouvir(LembreteService.ListaMudou.class,
                e -> Platform.runLater(this::recarregarHoje));

        recarregar();
    }

    public void recarregar() {
        atualizarCabecalho();
        recarregarHoje();
        recarregarRecados();
    }

    // ------------------------------------------------------------ cabeçalho

    private VBox montarCabecalho() {
        saudacao.getStyleClass().add("titulo-tela");
        resumoDoDia.getStyleClass().add("subtitulo");
        return new VBox(4, saudacao, resumoDoDia);
    }

    private void atualizarCabecalho() {
        LocalDateTime agora = LocalDateTime.now();
        int hora = agora.getHour();

        String tratamento;
        if (hora < 12) {
            tratamento = "Bom dia";
        } else if (hora < 18) {
            tratamento = "Boa tarde";
        } else {
            tratamento = "Boa noite";
        }
        saudacao.setText(tratamento + "!");

        String diaDaSemana = agora.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        String data = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",
                new Locale("pt", "BR")).format(agora);

        long faltam = servico.quantosFaltamHoje();
        String pendencia = switch ((int) Math.min(faltam, 2)) {
            case 0 -> "nada mais marcado para hoje";
            case 1 -> "1 compromisso ainda por vir";
            default -> faltam + " compromissos ainda por vir";
        };

        resumoDoDia.setText(maiusculaInicial(diaDaSemana) + ", " + data + "  •  " + pendencia);
    }

    private String maiusculaInicial(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    // ----------------------------------------------------------- hoje

    /** O painel do dia. É uma {@link Secao}, como qualquer bloco do app. */
    private Secao montarPainelHoje() {
        return new Secao(Icone.Simbolo.CALENDARIO, "HOJE", listaHoje);
    }

    private void recarregarHoje() {
        listaHoje.getChildren().clear();

        List<InicioService.CompromissoDoDia> hoje = servico.agendaDeHoje();

        if (hoje.isEmpty()) {
            listaHoje.getChildren().add(nadaParaHoje());
            atualizarCabecalho();
            return;
        }

        boolean separadorPosto = false;
        for (InicioService.CompromissoDoDia item : hoje) {
            // Uma linha divide o que já passou do que ainda vem.
            if (!separadorPosto && !item.jaPassou()
                    && listaHoje.getChildren().size() > 0) {
                listaHoje.getChildren().add(divisorAgora());
                separadorPosto = true;
            }
            listaHoje.getChildren().add(linhaDoCompromisso(item));
        }

        List<InicioService.CompromissoDoDia> proximos = servico.proximosDias(3);
        if (!proximos.isEmpty()) {
            Label rotulo = new Label("Próximos dias");
            rotulo.getStyleClass().add("secao-campo");
            VBox.setMargin(rotulo, new Insets(10, 0, 0, 0));
            listaHoje.getChildren().add(rotulo);

            proximos.stream().limit(5).forEach(item ->
                    listaHoje.getChildren().add(linhaDoCompromisso(item)));
        }

        atualizarCabecalho();
    }

    private HBox linhaDoCompromisso(InicioService.CompromissoDoDia item) {
        boolean ehHoje = item.quando().toLocalDate().equals(LocalDate.now());

        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-cor");
        faixa.setStyle("-fx-background-color: " + corSegura(item.lembrete().getCor()) + ";");

        Label hora = new Label(ehHoje
                ? HORA.format(item.quando())
                : DateTimeFormatter.ofPattern("dd/MM HH:mm").format(item.quando()));
        hora.getStyleClass().add("hora-compromisso");

        boolean protegido = item.lembrete().isSensivel() && !SecurityService.estaDestrancado();
        Label titulo = new Label(protegido ? "Lembrete protegido" : item.lembrete().getTitulo());
        if (protegido) {
            titulo.setGraphic(Icone.de(Icone.Simbolo.CADEADO, 14, "icone-atencao"));
        }
        titulo.getStyleClass().add("titulo-compromisso");
        titulo.setWrapText(true);

        VBox textos = new VBox(2, titulo);
        if (!protegido && item.lembrete().getDescricao() != null
                && !item.lembrete().getDescricao().isBlank()) {
            Label descricao = new Label(item.lembrete().getDescricao());
            descricao.getStyleClass().add("secao-dica");
            descricao.setWrapText(true);
            descricao.setMaxWidth(520);
            textos.getChildren().add(descricao);
        }

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox linha = new HBox(14, faixa, hora, textos, espaco);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("linha-compromisso");

        if (item.jaPassou()) {
            linha.getStyleClass().add("passou");
        }
        if (item.eIminente()) {
            linha.getStyleClass().add("iminente");
            Label agora = new Label("em " + minutosAte(item.quando()) + " min");
            agora.getStyleClass().addAll("etiqueta", "etiqueta-hoje");
            linha.getChildren().add(agora);
        }
        return linha;
    }

    private long minutosAte(LocalDateTime quando) {
        return Math.max(0, ChronoUnit.MINUTES.between(LocalDateTime.now(), quando));
    }

    private Region divisorAgora() {
        Label agora = new Label("agora");
        agora.getStyleClass().add("divisor-agora-texto");

        Region linha = new Region();
        linha.getStyleClass().add("divisor-agora-linha");
        HBox.setHgrow(linha, Priority.ALWAYS);

        HBox caixa = new HBox(10, agora, linha);
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.getStyleClass().add("divisor-agora");
        return caixa;
    }

    private EstadoVazio nadaParaHoje() {
        return new EstadoVazio(Icone.Simbolo.CAFE, "Nenhum compromisso marcado para hoje.")
                .comExplicacao("O que tiver hora marcada nos lembretes aparece aqui, "
                        + "em ordem, com o que já passou separado do que ainda vem.")
                .comAcao("Criar um lembrete", this::abrirLembretes);
    }

    private void abrirLembretes() {
        // A navegação entre módulos é do Shell; daqui só pedimos a troca.
        EventBus.publicar(new br.com.myapp.ui.Shell.PedidoDeNavegacao("lembretes"));
    }

    // ----------------------------------------------------------- recados

    /**
     * O mural. O "Novo recado" fica no cabeçalho do próprio bloco, como o
     * "Copiar tudo" das notas: é uma ação do mural, não da tela inteira.
     */
    private Secao montarMural() {
        Button novo = Botoes.miudo("Novo recado", Icone.Simbolo.ADICIONAR);
        novo.setOnAction(e -> abrirEditor(null));

        muralRecados.setAlignment(Pos.TOP_LEFT);
        muralRecados.setPadding(new Insets(4, 0, 0, 0));

        return new Secao(Icone.Simbolo.RECADO, "RECADOS", areaMural).comAcoes(novo);
    }

    private void recarregarRecados() {
        muralRecados.getChildren().clear();

        List<Recado> recados = servico.listarRecados();

        if (recados.isEmpty()) {
            areaMural.getChildren().setAll(muralVazio());
            return;
        }
        areaMural.getChildren().setAll(muralRecados);

        for (Recado recado : recados) {
            muralRecados.getChildren().add(new PostIt(recado,
                    this::abrirEditor,
                    this::confirmarExclusao,
                    servico::trocarCor));
        }

        // Arrastar para reordenar: a tela mexe na lista e o serviço grava.
        Arrastavel.instalar(muralRecados, recados.size(), (de, para) -> {
            Arrastavel.mover(recados, de, para);
            servico.reordenar(recados);
        });
    }

    private EstadoVazio muralVazio() {
        return new EstadoVazio(Icone.Simbolo.RECADO, "Nenhum recado colado aqui.")
                .comExplicacao("Recados são bilhetes rápidos, do tamanho de um papel adesivo. "
                        + "Arraste para mudar de lugar e escolha a cor pelo botão direito.")
                .comAcao("Colar o primeiro recado", () -> abrirEditor(null));
    }

    private void abrirEditor(Recado recado) {
        boolean ehNovo = recado == null;
        new EditorRecado(getScene().getWindow(), recado).abrir().ifPresent(preenchido -> {
            try {
                servico.salvarRecado(preenchido);
                Aviso.sucesso(ehNovo ? "Recado colado no mural!" : "Recado atualizado.");
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            }
        });
    }

    private void confirmarExclusao(Recado recado) {
        boolean confirmou = Dialogos.confirmarExclusao(getScene().getWindow(),
                "este recado",
                "Ele sai do mural, mas continua guardado no banco.");
        if (confirmou) {
            servico.excluirRecado(recado.getId());
            Aviso.sucesso("Recado tirado da tela.");
        }
    }

    // -------------------------------------------------------------- apoio

    private String corSegura(String cor) {
        try {
            if (cor != null && !cor.isBlank()) {
                Color.web(cor);
                return cor;
            }
        } catch (Exception ignorado) {
            // cai no padrão
        }
        return "#4c8dff";
    }

    private Tooltip dica(String texto) {
        return Botoes.dica(texto);
    }
}
