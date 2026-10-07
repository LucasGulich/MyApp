package br.com.myapp.modules.inicio;

import br.com.myapp.agendas.Agenda;
import br.com.myapp.agendas.AgendaService;
import br.com.myapp.agendas.DialogoEvento;
import br.com.myapp.agendas.PainelAgendas;
import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.modules.lembretes.LembreteEditor;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Arrastavel;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /** O minuto que a agenda mostra; quando o relógio passa dele, redesenha. */
    private LocalDateTime ultimoMinutoDesenhado;

    // --- visões da agenda ---

    /** Como a agenda está sendo vista. O nome é o que vai para o config.json. */
    enum Visao {
        DIA("Dia"), SEMANA("Semana"), MES("Mês"), ANO("Ano");

        final String rotulo;

        Visao(String rotulo) {
            this.rotulo = rotulo;
        }

        static Visao de(String nome) {
            try {
                return Visao.valueOf(nome);
            } catch (Exception e) {
                return DIA;
            }
        }
    }

    private Visao visao = Visao.de(Config.get().visaoAgenda);

    /** Um dia dentro do período mostrado: o dia, a semana, o mês ou o ano dele. */
    private LocalDate referencia = LocalDate.now();

    private final Label tituloPeriodo = new Label();
    private final VBox areaAgenda = new VBox();
    private final ToggleGroup grupoVisoes = new ToggleGroup();

    // --- sincronizar as agendas do Google ---

    /** Só aparece com alguma agenda do Google conectada. */
    private final Button botaoSincronizar = new Button();
    private final Node iconeSincronizar = Icone.de(Icone.Simbolo.GERAR, 15);
    private final Tooltip dicaSincronizar = Botoes.instalarDica(botaoSincronizar, "");
    private final RotateTransition girando = new RotateTransition(Duration.millis(900), iconeSincronizar);

    private final CalendarioGrade.Acoes acoesDaGrade = new CalendarioGrade.Acoes() {
        @Override
        public void abrir(InicioService.CompromissoDoDia item) {
            abrirItem(item);
        }

        @Override
        public void irParaDia(LocalDate dia) {
            mudarVisao(Visao.DIA, dia);
        }

        @Override
        public void irParaMes(LocalDate diaDoMes) {
            mudarVisao(Visao.MES, diaDoMes);
        }
    };

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
        EventBus.ouvir(AgendaService.AgendasMudaram.class,
                e -> Platform.runLater(this::recarregarHoje));
        // Trancar e destrancar mostram e escondem os títulos protegidos.
        EventBus.ouvir(SecurityService.EstadoMudou.class,
                e -> Platform.runLater(this::recarregarHoje));

        recarregar();
        manterRelogioEmDia();
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

    // ----------------------------------------------------------- agenda

    /**
     * O painel da agenda, com a barra de navegação em cima:
     *
     * <pre>
     *   [‹] [Hoje] [›]  Outubro de 2026          [Dia|Semana|Mês|Ano]
     * </pre>
     *
     * É uma {@link Secao}, como qualquer bloco do app.
     */
    private Secao montarPainelHoje() {
        // ‹ Hoje › na mesma pílula do seletor de visão: os dois controles da
        // barra falam a mesma língua visual.
        Button anterior = botaoDaPilula(Icone.Simbolo.SETA_ESQUERDA, "Anterior");
        anterior.setOnAction(e -> andar(-1));
        Button seguinte = botaoDaPilula(Icone.Simbolo.SETA_DIREITA, "Seguinte");
        seguinte.setOnAction(e -> andar(1));
        Button hoje = new Button("Hoje");
        Botoes.instalarDica(hoje, "Voltar para o período de hoje");
        hoje.setOnAction(e -> mudarVisao(visao, LocalDate.now()));
        HBox navegacao = new HBox(anterior, hoje, seguinte);
        // Com pouco espaço, quem cede é o título do período (vira "..."); os
        // botões mantêm o tamanho — "..." no lugar de "Hoje" ou "Semana" não
        // diz nada.
        navegacao.setMinWidth(Region.USE_PREF_SIZE);
        navegacao.getStyleClass().add("seletor-visao");
        navegacao.setAlignment(Pos.CENTER_LEFT);

        tituloPeriodo.getStyleClass().add("titulo-periodo");
        tituloPeriodo.setMinWidth(0);

        HBox seletor = new HBox(0);
        seletor.getStyleClass().add("seletor-visao");
        for (Visao v : Visao.values()) {
            ToggleButton botao = new ToggleButton(v.rotulo);
            botao.setToggleGroup(grupoVisoes);
            botao.setUserData(v);
            botao.setSelected(v == visao);
            botao.setOnAction(e -> {
                // Clicar na visão já escolhida não pode deixar nenhuma marcada.
                botao.setSelected(true);
                mudarVisao(v, referencia);
            });
            seletor.getChildren().add(botao);
        }
        seletor.setMinWidth(Region.USE_PREF_SIZE);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox barra = new HBox(12, navegacao, tituloPeriodo, espaco, seletor);
        barra.setAlignment(Pos.CENTER_LEFT);

        botaoSincronizar.getStyleClass().add("botao-icone-miudo");
        botaoSincronizar.setGraphic(iconeSincronizar);
        botaoSincronizar.setOnAction(e -> sincronizarAgendas());

        return new Secao(Icone.Simbolo.CALENDARIO, "AGENDA", barra, areaAgenda)
                .comAcoes(botaoSincronizar);
    }

    private static Button botaoDaPilula(Icone.Simbolo simbolo, String dica) {
        Button b = new Button();
        b.setGraphic(Icone.de(simbolo, 15));
        Botoes.instalarDica(b, dica);
        return b;
    }

    /**
     * Mostra o botão só quando há agenda conectada, e mantém a dica em dia:
     * quais agendas, e quando foi a última sincronização.
     */
    private void atualizarBotaoSincronizar() {
        List<Agenda> agendas = AgendaService.listar();
        botaoSincronizar.setVisible(!agendas.isEmpty());
        botaoSincronizar.setManaged(!agendas.isEmpty());
        if (agendas.isEmpty()) {
            return;
        }
        String nomes = agendas.stream().map(Agenda::getNome).collect(Collectors.joining(", "));
        String ultima = agendas.stream()
                .map(Agenda::getSincronizadaEm)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(q -> "Última: " + PainelAgendas.haQuanto(q) + ".")
                .orElse("Ainda não sincronizou.");
        dicaSincronizar.setText("Sincronizar agora "
                + (agendas.size() == 1 ? "a agenda do Google (" : "as agendas do Google (") + nomes + ").\n"
                + "Elas já sincronizam sozinhas a cada " + AgendaService.MINUTOS_ENTRE_SINCRONIZACOES
                + " min. " + ultima);
    }

    /**
     * Baixa todas as agendas agora, fora da thread da tela. O ícone gira
     * enquanto isso, e o aviso no fim diz como foi.
     */
    private void sincronizarAgendas() {
        List<Agenda> agendas = AgendaService.listar();
        // Recém-aberto e ainda trancado, o MyApp não sabe os endereços: eles
        // vão cifrados, e a chave só vem com a senha. Ver a decisão 43.
        if (agendas.stream().noneMatch(a -> AgendaService.link(a.getId()).isPresent())) {
            Aviso.info("Destranque o MyApp uma vez para as agendas poderem sincronizar.");
            return;
        }
        botaoSincronizar.setDisable(true);
        girando.setByAngle(360);
        girando.setCycleCount(Animation.INDEFINITE);
        girando.setInterpolator(Interpolator.LINEAR);
        girando.play();

        new Thread(() -> {
            List<String> falharam = new ArrayList<>();
            for (Agenda a : agendas) {
                if (!AgendaService.sincronizar(a.getId())) {
                    falharam.add(a.getNome());
                }
            }
            Platform.runLater(() -> {
                girando.stop();
                iconeSincronizar.setRotate(0);
                botaoSincronizar.setDisable(false);
                if (falharam.isEmpty()) {
                    Aviso.sucesso(agendas.size() == 1 ? "Agenda sincronizada!" : "Agendas sincronizadas!");
                } else {
                    Aviso.erro("Não sincronizou: " + String.join(", ", falharam)
                            + ". Veja o motivo em Configurações → Agendas.");
                }
                atualizarBotaoSincronizar();
            });
        }, "sincronizar-agendas").start();
    }

    /** Troca de visão e/ou de período, lembrando a visão para a próxima abertura. */
    private void mudarVisao(Visao nova, LocalDate dia) {
        if (nova != visao) {
            visao = nova;
            Config.get().visaoAgenda = nova.name();
            Config.get().salvar();
        }
        referencia = dia;
        grupoVisoes.getToggles().forEach(t -> t.setSelected(t.getUserData() == visao));
        recarregarHoje();
    }

    /** ‹ e ›: um dia, uma semana, um mês ou um ano, conforme a visão. */
    private void andar(int passos) {
        referencia = switch (visao) {
            case DIA -> referencia.plusDays(passos);
            case SEMANA -> referencia.plusWeeks(passos);
            case MES -> referencia.plusMonths(passos);
            case ANO -> referencia.plusYears(passos);
        };
        recarregarHoje();
    }

    private void recarregarHoje() {
        LocalDateTime agora = LocalDateTime.now();
        // Virou o dia com a agenda mostrando "hoje": acompanha o relógio.
        if (ultimoMinutoDesenhado != null
                && !agora.toLocalDate().equals(ultimoMinutoDesenhado.toLocalDate())
                && referencia.equals(ultimoMinutoDesenhado.toLocalDate())) {
            referencia = agora.toLocalDate();
        }
        ultimoMinutoDesenhado = agora.truncatedTo(ChronoUnit.MINUTES);

        tituloPeriodo.setText(tituloDoPeriodo());
        areaAgenda.getChildren().clear();
        switch (visao) {
            case DIA -> {
                desenharDia(referencia);
                areaAgenda.getChildren().add(listaHoje);
            }
            case SEMANA -> {
                LocalDate inicio = CalendarioGrade.inicioDaSemana(referencia);
                areaAgenda.getChildren().add(CalendarioGrade.semana(referencia,
                        servico.porDia(inicio, inicio.plusDays(6)), acoesDaGrade));
            }
            case MES -> {
                YearMonth mes = YearMonth.from(referencia);
                LocalDate de = CalendarioGrade.inicioDaSemana(mes.atDay(1));
                areaAgenda.getChildren().add(CalendarioGrade.mes(referencia,
                        servico.porDia(de, de.plusWeeks(6)), acoesDaGrade));
            }
            case ANO -> areaAgenda.getChildren().add(CalendarioGrade.ano(referencia.getYear(),
                    servico.porDia(LocalDate.of(referencia.getYear(), 1, 1),
                            LocalDate.of(referencia.getYear(), 12, 31)), acoesDaGrade));
        }
        atualizarCabecalho();
        atualizarBotaoSincronizar();
    }

    private String tituloDoPeriodo() {
        LocalDate hoje = LocalDate.now();
        return switch (visao) {
            case DIA -> {
                String dia = CalendarioGrade.maiuscula(DateTimeFormatter
                        .ofPattern("EEEE, d 'de' MMMM", CalendarioGrade.PT_BR).format(referencia));
                if (referencia.equals(hoje)) {
                    yield "Hoje · " + dia;
                }
                if (referencia.equals(hoje.plusDays(1))) {
                    yield "Amanhã · " + dia;
                }
                if (referencia.equals(hoje.minusDays(1))) {
                    yield "Ontem · " + dia;
                }
                yield referencia.getYear() == hoje.getYear() ? dia : dia + " de " + referencia.getYear();
            }
            case SEMANA -> {
                LocalDate inicio = CalendarioGrade.inicioDaSemana(referencia);
                LocalDate fim = inicio.plusDays(6);
                DateTimeFormatter diaMes = DateTimeFormatter.ofPattern("d 'de' MMMM", CalendarioGrade.PT_BR);
                yield inicio.getMonth() == fim.getMonth()
                        ? inicio.getDayOfMonth() + " – " + diaMes.format(fim) + " de " + fim.getYear()
                        : diaMes.format(inicio) + " – " + diaMes.format(fim) + " de " + fim.getYear();
            }
            case MES -> CalendarioGrade.maiuscula(DateTimeFormatter
                    .ofPattern("MMMM 'de' yyyy", CalendarioGrade.PT_BR).format(referencia));
            case ANO -> String.valueOf(referencia.getYear());
        };
    }

    /**
     * A visão de dia: a lista com horário. Para hoje, com a linha da hora
     * atual e os próximos dias embaixo; para outro dia, só o dia.
     */
    private void desenharDia(LocalDate dia) {
        listaHoje.getChildren().clear();
        boolean ehHoje = dia.equals(LocalDate.now());

        List<AgendaService.EventoNaAgenda> diaInteiro = servico.diaInteiroDoDia(dia);
        if (!diaInteiro.isEmpty()) {
            listaHoje.getChildren().add(faixaDiaInteiro(diaInteiro));
        }

        List<InicioService.CompromissoDoDia> doDia = servico.agendaDoDia(dia);

        if (doDia.isEmpty()) {
            listaHoje.getChildren().add(ehHoje ? nadaParaHoje() : nadaNesteDia());
            return;
        }

        if (!ehHoje) {
            doDia.forEach(item -> listaHoje.getChildren().add(linhaDoCompromisso(item)));
            return;
        }

        // A linha da hora atual divide o que já passou do que ainda vem. Entra
        // sempre — no topo se nada passou, no fim se tudo passou —, porque é o
        // "você está aqui" da lista: some ora sim, ora não, e ninguém aprende
        // o que ela quer dizer.
        //
        // Uma reunião em andamento fica abaixo da linha (ainda não passou),
        // mas o "próximo em…" fala do que ainda vai começar.
        LocalDateTime agora = LocalDateTime.now();
        InicioService.CompromissoDoDia proximo = doDia.stream()
                .filter(c -> !c.quando().isBefore(agora))
                .findFirst().orElse(null);
        boolean separadorPosto = false;
        for (InicioService.CompromissoDoDia item : doDia) {
            if (!separadorPosto && !item.jaPassou()) {
                listaHoje.getChildren().add(divisorAgora(proximo));
                separadorPosto = true;
            }
            listaHoje.getChildren().add(linhaDoCompromisso(item));
        }
        if (!separadorPosto) {
            listaHoje.getChildren().add(divisorAgora(null));
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
    }

    /**
     * Abre o que foi clicado: o evento do Google no painel de detalhes, o
     * lembrete no editor.
     *
     * Trancado, não abre nada. A tela inicial funciona trancada, e o editor
     * de um lembrete protegido mostraria "Lembrete protegido" no lugar do
     * título — salvar dali gravaria isso por cima do título de verdade.
     */
    private void abrirItem(InicioService.CompromissoDoDia item) {
        if (!SecurityService.estaDestrancado()) {
            Aviso.info("Destranque o MyApp para abrir.");
            return;
        }
        Window janela = getScene() == null ? null : getScene().getWindow();
        if (item.ehDaAgenda()) {
            new DialogoEvento(janela, item.evento()).abrir();
            return;
        }
        new LembreteEditor(janela, item.lembrete()).abrir().ifPresent(l -> {
            try {
                lembretes.salvar(l);
                Aviso.sucesso("Lembrete atualizado.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                Dialogos.erro(janela, "Não foi possível salvar", e.getMessage());
            }
        });
    }

    private EstadoVazio nadaNesteDia() {
        return new EstadoVazio(Icone.Simbolo.CAFE, "Nada marcado para este dia.")
                .comAcao("Criar um lembrete", this::abrirLembretes);
    }

    private HBox linhaDoCompromisso(InicioService.CompromissoDoDia item) {
        boolean ehHoje = item.quando().toLocalDate().equals(LocalDate.now());
        boolean diaTodo = item.ehDaAgenda() && item.evento().evento().diaInteiro();

        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-cor");
        faixa.setStyle("-fx-background-color: " + corSegura(item.cor()) + ";");

        Label hora = new Label(diaTodo
                ? DateTimeFormatter.ofPattern("dd/MM").format(item.quando())
                : ehHoje
                ? HORA.format(item.quando())
                : DateTimeFormatter.ofPattern("dd/MM HH:mm").format(item.quando()));
        hora.getStyleClass().add("hora-compromisso");

        // Evento de agenda tem duração: o término vai embaixo, miúdo, para a
        // coluna de horários continuar alinhada.
        VBox horario = new VBox(1, hora);
        String complemento = null;
        if (diaTodo) {
            complemento = "dia todo";
        } else if (item.fim() != null && item.fim().isAfter(item.quando())
                && item.fim().toLocalDate().equals(item.quando().toLocalDate())) {
            complemento = "até " + HORA.format(item.fim());
        }
        if (complemento != null) {
            Label ate = new Label(complemento);
            ate.getStyleClass().add("hora-compromisso-fim");
            horario.getChildren().add(ate);
        }

        boolean protegido = item.protegido() && !SecurityService.estaDestrancado();
        Label titulo = new Label(protegido
                ? (item.ehDaAgenda() ? "Evento protegido" : "Lembrete protegido")
                : item.titulo());
        if (protegido) {
            titulo.setGraphic(Icone.de(Icone.Simbolo.CADEADO, 14, "icone-atencao"));
        }
        titulo.getStyleClass().add("titulo-compromisso");
        titulo.setWrapText(true);

        VBox textos = new VBox(2, titulo);
        if (!protegido && item.ehDaAgenda() && !item.evento().evento().local().isBlank()) {
            Label local = new Label("Local: " + item.evento().evento().local());
            local.getStyleClass().add("secao-dica");
            local.setWrapText(true);
            local.setMaxWidth(520);
            textos.getChildren().add(local);
        }
        if (!protegido && item.descricao() != null && !item.descricao().isBlank()) {
            Label descricao = new Label(item.descricao());
            descricao.getStyleClass().add("secao-dica");
            descricao.setWrapText(true);
            descricao.setMaxWidth(520);
            // Descrição de reunião do Google costuma ser longa: a linha da
            // agenda mostra o começo, não a pauta inteira.
            descricao.setMaxHeight(36);
            textos.getChildren().add(descricao);
        }

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox linha = new HBox(14, faixa, horario, textos, espaco);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("linha-compromisso");
        // O clique abre o detalhe; o "Entrar" consome o próprio clique.
        linha.getStyleClass().add("clicavel");
        linha.setOnMouseClicked(e -> abrirItem(item));

        if (item.jaPassou()) {
            linha.getStyleClass().add("passou");
        }
        if (item.emAndamento()) {
            linha.getStyleClass().add("iminente");
            Label acontecendo = new Label("acontecendo");
            acontecendo.getStyleClass().addAll("etiqueta", "etiqueta-andamento");
            linha.getChildren().add(acontecendo);
        } else if (item.eIminente()) {
            linha.getStyleClass().add("iminente");
            Label agora = new Label("em " + minutosAte(item.quando()) + " min");
            agora.getStyleClass().addAll("etiqueta", "etiqueta-hoje");
            linha.getChildren().add(agora);
        }

        if (item.ehDaAgenda()) {
            String link = item.evento().evento().linkReuniao();
            if (link != null && !protegido && !item.jaPassou()) {
                Button entrar = Botoes.miudo("Entrar", Icone.Simbolo.ABRIR_FORA);
                entrar.getStyleClass().add("botao-entrar");
                Botoes.instalarDica(entrar, "Abrir a reunião no navegador");
                entrar.setOnAction(e -> abrirNoNavegador(link));
                entrar.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
                linha.getChildren().add(entrar);
            }
            linha.getChildren().add(seloDaAgenda(item.evento().agenda()));
        }
        return linha;
    }

    /**
     * O "G" no canto: diz de onde o evento veio sem disputar atenção com o
     * título. O nome da agenda aparece ao parar o mouse.
     */
    private Label seloDaAgenda(Agenda agenda) {
        Label selo = new Label("G");
        selo.getStyleClass().add("selo-agenda");
        Botoes.instalarDica(selo, "Google Agenda · " + agenda.getNome()
                + ". Para alterar o evento, use o Google Agenda.");
        return selo;
    }

    /** Aniversários, feriados e férias de hoje, em fichas no topo do painel. */
    private HBox faixaDiaInteiro(List<AgendaService.EventoNaAgenda> eventos) {
        Label rotulo = new Label("DIA INTEIRO");
        rotulo.getStyleClass().add("dia-inteiro-rotulo");

        FlowPane fichas = new FlowPane(8, 6);
        boolean trancado = !SecurityService.estaDestrancado();
        for (AgendaService.EventoNaAgenda e : eventos) {
            boolean protegido = e.agenda().isProtegida() && trancado;
            Label ficha = new Label(protegido ? "Evento protegido" : e.evento().titulo());
            ficha.getStyleClass().add("ficha-dia-inteiro");
            ficha.getStyleClass().add("clicavel");
            ficha.setOnMouseClicked(clique -> abrirItem(new InicioService.CompromissoDoDia(null, e,
                    e.evento().inicio())));
            ficha.setStyle("-fx-border-color: " + corSegura(e.agenda().getCor()) + ";");
            Botoes.instalarDica(ficha, "Google Agenda · " + e.agenda().getNome());
            fichas.getChildren().add(ficha);
        }
        HBox.setHgrow(fichas, Priority.ALWAYS);

        HBox faixa = new HBox(12, rotulo, fichas);
        faixa.setAlignment(Pos.CENTER_LEFT);
        faixa.getStyleClass().add("faixa-dia-inteiro");
        return faixa;
    }

    private void abrirNoNavegador(String link) {
        // Fora da thread da tela: o navegador às vezes demora a responder, e
        // a janela não pode congelar esperando.
        new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(link));
            } catch (Exception e) {
                Platform.runLater(() -> Aviso.erro("Não foi possível abrir o link da reunião."));
            }
        }, "abrir-reuniao").start();
    }

    private long minutosAte(LocalDateTime quando) {
        return Math.max(0, ChronoUnit.MINUTES.between(LocalDateTime.now(), quando));
    }

    /**
     * A linha da hora atual: "14:21 · próximo em 3 h 39 min ●────────".
     *
     * Mostra o relógio, e não a palavra "agora": um "agora" em vermelho logo
     * acima de um compromisso das 18h parecia dizer que ele era agora.
     *
     * @param proximo o primeiro compromisso que ainda vem, ou null se o dia acabou
     */
    private Region divisorAgora(InicioService.CompromissoDoDia proximo) {
        LocalDateTime agora = LocalDateTime.now();

        Label hora = new Label(HORA.format(agora));
        hora.getStyleClass().add("divisor-agora-hora");

        Label falta = new Label(proximo == null
                ? "·  nada mais hoje"
                : "·  próximo " + quantoFalta(agora, proximo.quando()));
        falta.getStyleClass().add("divisor-agora-texto");

        Circle ponto = new Circle(3.5);
        ponto.getStyleClass().add("divisor-agora-ponto");

        Region linha = new Region();
        linha.getStyleClass().add("divisor-agora-linha");
        HBox.setHgrow(linha, Priority.ALWAYS);

        HBox trilho = new HBox(0, ponto, linha);
        trilho.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(trilho, Priority.ALWAYS);

        HBox caixa = new HBox(8, hora, falta, trilho);
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.getStyleClass().add("divisor-agora");
        Botoes.instalarDica(caixa, "Hora atual. Acima da linha, o que já passou hoje; "
                + "abaixo, o que ainda vem. Ela anda sozinha a cada minuto.");
        return caixa;
    }

    /** "em 12 min", "em 3 h 39 min", "em 2 h" — ou "agora", no minuto exato. */
    private static String quantoFalta(LocalDateTime agora, LocalDateTime quando) {
        long minutos = ChronoUnit.MINUTES.between(agora, quando);
        if (minutos < 1) {
            return "agora";
        }
        if (minutos < 60) {
            return "em " + minutos + " min";
        }
        long horas = minutos / 60;
        long resto = minutos % 60;
        return resto == 0 ? "em " + horas + " h" : "em " + horas + " h " + resto + " min";
    }

    /**
     * Mantém a tela viva sem ninguém mexer: a linha da hora anda, o que passou
     * fica apagado e a saudação vira "Boa tarde" na hora certa.
     *
     * Confere o relógio a cada poucos segundos, mas só redesenha quando o
     * minuto muda. Conferir em vez de agendar "daqui a 60 s" também acerta a
     * tela depois de o computador voltar da suspensão. Os recados ficam de
     * fora: redesenhá-los interromperia um arrasto no meio.
     */
    private void manterRelogioEmDia() {
        Timeline relogio = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            LocalDateTime minuto = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            if (minuto.equals(ultimoMinutoDesenhado)) {
                return;
            }
            // Mês e ano só mudam com o dia: redesenhar o ano inteiro a cada
            // minuto seria trabalho à toa. Dia e semana mostram o que já passou
            // apagado, e isso anda com o relógio.
            boolean virouODia = ultimoMinutoDesenhado == null
                    || !minuto.toLocalDate().equals(ultimoMinutoDesenhado.toLocalDate());
            if (virouODia || visao == Visao.DIA || visao == Visao.SEMANA) {
                recarregarHoje();
            } else {
                ultimoMinutoDesenhado = minuto;
                atualizarCabecalho();
            }
        }));
        relogio.setCycleCount(Animation.INDEFINITE);
        relogio.play();
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
