package br.com.myapp.modules.lembretes;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Texto;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Arrastavel;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.CampoBusca;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Tela principal dos lembretes.
 *
 * Mostra a lista ordenada pelo que acontece primeiro, com filtro por texto e
 * por situacao. Cada cartao traz o próximo disparo em linguagem do dia a dia
 * ("em 2 horas", "amanhã às 09:00") em vez de apenas a data crua.
 */
public class LembretesView extends BorderPane {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter SO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final LembreteService servico = new LembreteService();

    private final VBox lista = new VBox(10);
    private final CampoBusca busca = new CampoBusca("Buscar por título ou descrição...");
    private final ComboBox<String> filtro = new ComboBox<>();
    private final ComboBox<String> ordenacao = new ComboBox<>();
    private final Label contador = new Label();

    public LembretesView() {
        getStyleClass().add("conteudo");
        setTop(montarCabecalho());
        setCenter(montarLista());

        // A tela se redesenha sozinha quando algo muda em qualquer lugar do app.
        EventBus.ouvir(LembreteService.ListaMudou.class, e -> Platform.runLater(this::recarregar));

        recarregar();
    }

    // ------------------------------------------------------------- Cabeçalho

    private VBox montarCabecalho() {
        Label titulo = new Label("Lembretes");
        titulo.getStyleClass().add("titulo-tela");

        contador.getStyleClass().add("subtitulo");

        VBox textos = new VBox(2, titulo, contador);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Button novo = Botoes.primario("Novo lembrete", Icone.Simbolo.ADICIONAR);
        novo.setOnAction(e -> abrirEditor(null));

        HBox linhaTitulo = new HBox(12, textos, espaco, novo);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);

        busca.setPrefWidth(320);
        busca.textProperty().addListener((o, a, n) -> recarregar());

        filtro.getItems().addAll("Todos", "Ativos", "Pausados", "Hoje", "Esta semana", "Lixeira");
        filtro.setValue("Todos");
        filtro.getStyleClass().add("campo");
        filtro.setOnAction(e -> recarregar());

        ordenacao.getItems().addAll("Por proximidade", "Minha ordem");
        ordenacao.setValue(servico.temOrdemManual() ? "Minha ordem" : "Por proximidade");
        ordenacao.getStyleClass().add("campo");
        ordenacao.setTooltip(dica("Em \"Minha ordem\" você arrasta os cartões para onde quiser"));
        ordenacao.setOnAction(e -> recarregar());

        HBox linhaFiltros = new HBox(10, busca, filtro, ordenacao);
        linhaFiltros.setAlignment(Pos.CENTER_LEFT);
        linhaFiltros.setPadding(new Insets(14, 0, 0, 0));

        VBox cabecalho = new VBox(0, linhaTitulo, linhaFiltros);
        cabecalho.setPadding(new Insets(0, 0, 18, 0));
        return cabecalho;
    }

    private ScrollPane montarLista() {
        lista.setPadding(new Insets(2, 6, 20, 0));

        ScrollPane rolagem = new ScrollPane(lista);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return rolagem;
    }

    // ------------------------------------------------------------ recarregar

    public void recarregar() {
        lista.getChildren().clear();

        boolean naLixeira = "Lixeira".equals(filtro.getValue());
        boolean ordemManual = "Minha ordem".equals(ordenacao.getValue()) && !naLixeira;

        List<Lembrete> todos;
        if (naLixeira) {
            todos = servico.listarExcluidos();
        } else if (ordemManual) {
            todos = servico.listarNaMinhaOrdem();
        } else {
            todos = servico.listarPorProximidade();
        }

        List<Lembrete> visiveis = new java.util.ArrayList<>(
                todos.stream().filter(this::passaNoFiltro).toList());

        if (naLixeira) {
            contador.setText(todos.isEmpty()
                    ? "0 lembretes na lixeira"
                    : todos.size() + " lembrete(s) excluído(s) — nada foi apagado do banco");
        } else {
            long ativos = todos.stream().filter(Lembrete::isAtivo).count();
            contador.setText(todos.isEmpty()
                    ? "0 lembretes"
                    : ativos + " ativo(s) de " + todos.size() + " no total");
        }

        if (visiveis.isEmpty()) {
            lista.getChildren().add(montarEstadoVazio(todos.isEmpty()));
            return;
        }
        for (Lembrete lembrete : visiveis) {
            lista.getChildren().add(montarCartao(lembrete));
        }

        // Arrastar só faz sentido quando a ordem é sua: reposicionar um cartão
        // numa lista ordenada por horário seria desfeito no próximo desenho.
        if (ordemManual) {
            contador.setText(contador.getText() + "  •  arraste os cartões para reordenar");
            Arrastavel.instalar(lista, visiveis.size(), (de, para) -> {
                Arrastavel.mover(visiveis, de, para);
                servico.reordenar(visiveis);
            });
        }
    }

    private boolean passaNoFiltro(Lembrete lembrete) {
        String termo = busca.getText();
        if (!Texto.contem(lembrete.getTitulo(), termo) && !Texto.contem(lembrete.getDescricao(), termo)) {
            return false;
        }

        String modo = filtro.getValue();
        if (modo == null) {
            return true;
        }
        return switch (modo) {
            // A lixeira já vem filtrada da consulta; aqui só o texto se aplica.
            case "Lixeira" -> true;
            case "Ativos" -> lembrete.isAtivo();
            case "Pausados" -> !lembrete.isAtivo();
            case "Hoje" -> servico.proximaOcorrencia(lembrete)
                    .map(d -> d.toLocalDate().equals(LocalDate.now()))
                    .orElse(false);
            case "Esta semana" -> servico.proximaOcorrencia(lembrete)
                    .map(d -> !d.isAfter(LocalDateTime.now().plusDays(7)))
                    .orElse(false);
            default -> true;
        };
    }

    // --------------------------------------------------------------- cartoes

    private HBox montarCartao(Lembrete lembrete) {
        // Faixa colorida na lateral, para bater o olho e reconhecer o assunto.
        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-cor");
        faixa.setStyle("-fx-background-color: " + corSegura(lembrete.getCor()) + ";");

        // --- linha 1: Título e etiquetas ---
        Label titulo = new Label(lembrete.getTitulo());
        titulo.getStyleClass().add("texto");
        titulo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        titulo.setWrapText(true);

        HBox linhaTitulo = new HBox(8, titulo);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);

        if (lembrete.isSensivel()) {
            Label etiqueta = new Label("protegido");
            etiqueta.setGraphic(Icone.de(Icone.Simbolo.CADEADO, 11));
            etiqueta.getStyleClass().addAll("etiqueta", "etiqueta-protegido");
            linhaTitulo.getChildren().add(etiqueta);
        }

        Optional<LocalDateTime> proxima = servico.proximaOcorrencia(lembrete);
        if (proxima.isPresent() && proxima.get().toLocalDate().equals(LocalDate.now())) {
            Label hoje = new Label("hoje");
            hoje.getStyleClass().addAll("etiqueta", "etiqueta-hoje");
            linhaTitulo.getChildren().add(hoje);
        }

        if (!lembrete.isSomAtivo()) {
            Label silencioso = new Label("silencioso");
            silencioso.setGraphic(Icone.de(Icone.Simbolo.SEM_SOM, 11));
            silencioso.getStyleClass().add("etiqueta");
            linhaTitulo.getChildren().add(silencioso);
        }

        // --- linha 2: quando acontece ---
        Label quando = new Label(descreverProxima(lembrete, proxima));
        // Verde só quando há mesmo algo por vir; pausado ou vencido fica neutro.
        boolean temFuturo = lembrete.isAtivo() && proxima.isPresent();
        quando.getStyleClass().add(temFuturo ? "texto-ok" : "texto-fraco");
        quando.setStyle("-fx-font-size: 12px;");

        // --- linha 3: detalhes ---
        Label detalhes = new Label(lembrete.resumoRecorrencia() + "  •  " + descreverAvisos(lembrete));
        detalhes.getStyleClass().add("texto-fraco");

        VBox textos = new VBox(4, linhaTitulo, quando, detalhes);

        if (lembrete.getDescricao() != null && !lembrete.getDescricao().isBlank()) {
            Label descricao = new Label(lembrete.getDescricao());
            descricao.getStyleClass().add("texto-fraco");
            descricao.setWrapText(true);
            descricao.setMaxWidth(520);
            textos.getChildren().add(descricao);
        }

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        // --- Ações ---
        HBox acoes = new HBox(6);
        acoes.setAlignment(Pos.CENTER_RIGHT);

        if (lembrete.isExcluido()) {
            // Na lixeira só faz sentido trazer de volta.
            Button restaurar = botaoIcone(Icone.Simbolo.RESTAURAR, "Restaurar este lembrete");
            restaurar.setOnAction(e -> {
                servico.restaurar(lembrete.getId());
                Aviso.sucesso("Lembrete restaurado.");
                recarregar();
            });
            acoes.getChildren().add(restaurar);
        } else {
            Button pausar = botaoIcone(
                    lembrete.isAtivo() ? Icone.Simbolo.PAUSAR : Icone.Simbolo.RETOMAR,
                    lembrete.isAtivo()
                            ? "Pausar — para de alertar, sem perder o cadastro"
                            : "Reativar — volta a alertar normalmente");
            pausar.setOnAction(e -> {
                servico.alternarAtivo(lembrete);
                Aviso.info(lembrete.isAtivo()
                        ? "Lembrete reativado."
                        : "Lembrete pausado — não vai mais alertar.");
                recarregar();
            });

            Button editar = botaoIcone(Icone.Simbolo.EDITAR, "Editar este lembrete");
            editar.setOnAction(e -> abrirEditor(lembrete));

            Button duplicar = botaoIcone(Icone.Simbolo.DUPLICAR,
                    "Duplicar — cria uma cópia para você ajustar");
            duplicar.setOnAction(e -> {
                servico.duplicar(lembrete);
                Aviso.sucesso("Cópia criada.");
                recarregar();
            });

            Button excluir = botaoIcone(Icone.Simbolo.EXCLUIR,
                    "Excluir — vai para a lixeira e pode ser restaurado");
            excluir.getStyleClass().add("botao-icone-perigo");
            excluir.setOnAction(e -> confirmarExclusao(lembrete));

            acoes.getChildren().addAll(pausar, editar, duplicar, excluir);
        }

        HBox cartao = new HBox(14, faixa, textos, espaco, acoes);
        cartao.setAlignment(Pos.CENTER_LEFT);
        cartao.getStyleClass().add("cartao-lembrete");
        if (!lembrete.isAtivo() || lembrete.isExcluido()) {
            cartao.getStyleClass().add("inativo");
        }
        return cartao;
    }

    /** Botão de ação do cartão. */
    private Button botaoIcone(Icone.Simbolo simbolo, String texto) {
        return Botoes.icone(simbolo, texto);
    }

    /** Dica que aparece rápido e fica tempo bastante para ser lida. */
    private Tooltip dica(String texto) {
        return Botoes.dica(texto);
    }

    private EstadoVazio montarEstadoVazio(boolean nadaCadastrado) {
        if ("Lixeira".equals(filtro.getValue())) {
            return new EstadoVazio(Icone.Simbolo.EXCLUIR, "A lixeira está vazia.")
                    .comExplicacao("O que você excluir vem para cá e pode ser restaurado.");
        }
        if (!nadaCadastrado) {
            return new EstadoVazio(Icone.Simbolo.BUSCAR, "Nenhum lembrete corresponde ao filtro.")
                    .comExplicacao("Tente outra palavra na busca, ou volte o filtro para \"Todos\".");
        }
        return new EstadoVazio(Icone.Simbolo.LEMBRETE, "Você ainda não tem lembretes.")
                .comExplicacao("Um lembrete avisa na hora marcada — uma vez só ou repetindo, "
                        + "com quantos avisos antes você quiser.")
                .comAcao("Criar o primeiro lembrete", () -> abrirEditor(null));
    }

    // ----------------------------------------------------------- textos úteis

    /** Traduz a próxima ocorrência para linguagem do dia a dia. */
    private String descreverProxima(Lembrete lembrete, Optional<LocalDateTime> proxima) {
        if (!lembrete.isAtivo()) {
            return "Pausado";
        }
        if (proxima.isEmpty()) {
            return "Já passou - sem próximas datas";
        }
        LocalDateTime quando = proxima.get();
        LocalDateTime agora = LocalDateTime.now();
        long minutos = ChronoUnit.MINUTES.between(agora, quando);

        if (minutos < 1) {
            return "Acontecendo agora";
        }
        if (minutos < 60) {
            return "Em " + minutos + " min  (" + SO_HORA.format(quando) + ")";
        }
        if (quando.toLocalDate().equals(LocalDate.now())) {
            return "Hoje às " + SO_HORA.format(quando);
        }
        if (quando.toLocalDate().equals(LocalDate.now().plusDays(1))) {
            return "Amanhã às " + SO_HORA.format(quando);
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), quando.toLocalDate());
        if (dias <= 7) {
            return "Em " + dias + " dias  (" + DATA_HORA.format(quando) + ")";
        }
        return DATA_HORA.format(quando);
    }

    private String descreverAvisos(Lembrete lembrete) {
        List<Integer> avisos = lembrete.getAntecedencias();
        if (avisos.size() == 1 && avisos.get(0) == 0) {
            return "aviso na hora exata";
        }
        StringBuilder sb = new StringBuilder("avisa ");
        for (int i = 0; i < avisos.size(); i++) {
            if (i > 0) {
                sb.append(i == avisos.size() - 1 ? " e " : ", ");
            }
            sb.append(descreverAntecedencia(avisos.get(i)));
        }
        return sb.toString();
    }

    static String descreverAntecedencia(int minutos) {
        if (minutos == 0) {
            return "na hora";
        }
        if (minutos < 60) {
            return minutos + " min antes";
        }
        if (minutos % 1440 == 0) {
            int dias = minutos / 1440;
            return dias == 1 ? "1 dia antes" : dias + " dias antes";
        }
        if (minutos % 60 == 0) {
            int horas = minutos / 60;
            return horas == 1 ? "1 h antes" : horas + " h antes";
        }
        return (minutos / 60) + "h" + (minutos % 60) + " antes";
    }

    /** Evita que uma cor inválida gravada no banco quebre o estilo do cartao. */
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

    // ---------------------------------------------------------------- Ações

    private void abrirEditor(Lembrete lembrete) {
        boolean novo = lembrete == null;
        LembreteEditor editor = new LembreteEditor(getScene().getWindow(), lembrete);
        editor.abrir().ifPresent(salvo -> {
            try {
                servico.salvar(salvo);
                Aviso.sucesso(novo
                        ? "Lembrete criado com sucesso!"
                        : "Lembrete atualizado.");
                recarregar();
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            } catch (IllegalStateException e) {
                Dialogos.erro(getScene().getWindow(), "Conteúdo protegido", e.getMessage());
            }
        });
    }

    private void confirmarExclusao(Lembrete lembrete) {
        String nome = lembrete.isSensivel() && !SecurityService.estaDestrancado()
                ? "este lembrete protegido"
                : "\"" + lembrete.getTitulo() + "\"";

        boolean confirmou = Dialogos.confirmarExclusao(getScene().getWindow(),
                "o lembrete " + nome,
                "Ele sai da lista e para de alertar, mas continua guardado. "
                        + "Para trazê-lo de volta, use o filtro Lixeira.");

        if (confirmou) {
            servico.excluir(lembrete.getId());
            Aviso.sucesso("Lembrete movido para a lixeira.");
            recarregar();
        }
    }
}
