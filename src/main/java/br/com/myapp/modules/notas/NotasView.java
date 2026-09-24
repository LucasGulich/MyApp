package br.com.myapp.modules.notas;

import br.com.myapp.core.EventBus;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.AreaTransferencia;
import br.com.myapp.ui.Arrastavel;
import br.com.myapp.ui.Aviso;
import br.com.myapp.ui.Botoes;
import br.com.myapp.ui.CampoBusca;
import br.com.myapp.ui.Dialogos;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Secao;
import br.com.myapp.ui.TextoSelecionavel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tela do módulo de notas, em três colunas.
 *
 * <pre>
 *   categorias  │  lista de notas  │  a nota aberta
 * </pre>
 *
 * É o formato que aplicativos de nota consagraram, e não há motivo para
 * inventar outro: a coluna da esquerda responde "onde estou", a do meio "o que
 * tenho aqui", e a da direita "o que é isto".
 */
public class NotasView extends BorderPane {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final NotaService servico = new NotaService();

    // --- coluna 1 ---
    private final VBox listaCategorias = new VBox(4);

    /**
     * Só as categorias, dentro da coluna.
     *
     * Existe separada porque os atalhos de cima ("Todas", "Favoritas") e os
     * de baixo ("Sem categoria", "Lixeira") não são categorias e não podem
     * entrar no arrasto — e o reordenador trabalha sobre os filhos de um
     * painel, do primeiro ao último.
     */
    private final VBox listaSoCategorias = new VBox(4);

    // --- coluna 2 ---
    private final VBox listaNotas = new VBox(8);
    private final CampoBusca busca = new CampoBusca("Buscar nas notas...");
    private final Label contador = new Label();
    private final javafx.scene.control.ComboBox<String> ordenacao = new javafx.scene.control.ComboBox<>();

    // --- coluna 3 ---
    private final VBox painelDetalhe = new VBox(16);

    /** Filtro atual: id da categoria, ou um dos marcadores abaixo. */
    private Object filtroAtual = FILTRO_TODAS;

    private static final String FILTRO_TODAS = "todas";
    private static final String FILTRO_FAVORITAS = "favoritas";
    private static final String FILTRO_SEM_CATEGORIA = "sem-categoria";
    private static final String FILTRO_LIXEIRA = "lixeira";

    private Nota notaAberta;

    /**
     * Abaixo desta largura as três colunas não cabem, e a de categorias sai.
     *
     * <p>É a soma das larguras mínimas das três mais o respiro entre elas —
     * medido, não chutado: com a janela no tamanho padrão de 1100 px sobram
     * 872 px para esta tela.
     */
    private static final double LARGURA_PARA_TRES_COLUNAS = 900;

    private VBox colunaCategorias;
    private Button botaoCategorias;

    /** Havia espaço para três colunas no último desenho? */
    private boolean espacoParaTresColunas = true;

    /** O usuário escondeu as categorias por conta própria? */
    private boolean escondidaPeloUsuario;

    public NotasView() {
        getStyleClass().add("conteudo");

        servico.criarCategoriasIniciaisSeVazio();
        setCenter(montarTresColunas());

        EventBus.ouvir(NotaService.ListaMudou.class,
                e -> Platform.runLater(this::recarregarNotas));
        EventBus.ouvir(NotaService.CategoriasMudaram.class,
                e -> Platform.runLater(this::recarregarCategorias));

        recarregar();
    }

    /**
     * As três colunas, lado a lado.
     *
     * <p>Em um {@code BorderPane} isto não funcionava: ele entrega a largura
     * <b>preferida</b> aos painéis da esquerda e da direita e espreme o do
     * meio — que tem largura mínima e se recusa a encolher. Com a janela sem
     * maximizar, a soma passava da largura disponível e a coluna de detalhe
     * era empurrada para fora da tela, levando junto os botões de ação.
     *
     * <p>Em um {@code HBox} as colunas encolhem até o mínimo de cada uma, e a
     * de detalhe é a que absorve a sobra — é ela que tem conteúdo de largura
     * variável.
     */
    private HBox montarTresColunas() {
        colunaCategorias = montarColunaCategorias();
        Node colunaNotas = montarColunaNotas();
        Node colunaDetalhe = montarColunaDetalhe();

        HBox.setHgrow(colunaDetalhe, Priority.ALWAYS);

        HBox linha = new HBox(colunaCategorias, colunaNotas, colunaDetalhe);
        linha.widthProperty().addListener((o, a, largura) ->
                ajustarAoEspaco(largura.doubleValue()));
        return linha;
    }

    /**
     * Esconde a coluna de categorias quando não há espaço para as três.
     *
     * <p>É a primeira a sair porque é a que se usa de vez em quando — escolhe-se
     * a categoria e trabalha-se na lista e na nota. As outras duas são o
     * trabalho em si.
     *
     * <p>Quem esconde é a largura, não o usuário; o botão no topo da lista
     * permite trazê-la de volta quando ele quiser, mesmo apertado.
     */
    private void ajustarAoEspaco(double largura) {
        if (largura <= 0) {
            return;
        }
        boolean cabe = largura >= LARGURA_PARA_TRES_COLUNAS;
        if (cabe == espacoParaTresColunas) {
            return;
        }
        espacoParaTresColunas = cabe;

        // Uma vez escondida à mão, a coluna só volta por decisão de quem usa.
        if (!escondidaPeloUsuario) {
            mostrarCategorias(cabe);
        }
        botaoCategorias.setVisible(!cabe || escondidaPeloUsuario);
        botaoCategorias.setManaged(!cabe || escondidaPeloUsuario);
    }

    private void mostrarCategorias(boolean visivel) {
        colunaCategorias.setVisible(visivel);
        colunaCategorias.setManaged(visivel);
        Botoes.trocarIcone(botaoCategorias,
                visivel ? Icone.Simbolo.VOLTAR : Icone.Simbolo.ORDENAR);
    }

    public void recarregar() {
        recarregarCategorias();
        recarregarNotas();
    }

    // ------------------------------------------------- coluna 1: categorias

    private VBox montarColunaCategorias() {
        Label titulo = new Label("Categorias");
        titulo.getStyleClass().add("titulo-coluna");

        Button nova = Botoes.icone(Icone.Simbolo.ADICIONAR, "Criar uma categoria");
        nova.setOnAction(e -> abrirEditorDeCategoria(null));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox cabecalho = new HBox(8, titulo, espaco, nova);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        cabecalho.setPadding(new Insets(0, 0, 10, 0));

        ScrollPane rolagem = new ScrollPane(listaCategorias);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        VBox coluna = new VBox(0, cabecalho, rolagem);
        coluna.getStyleClass().add("coluna-categorias");
        return coluna;
    }

    private void recarregarCategorias() {
        listaCategorias.getChildren().clear();

        List<Categoria> categorias = servico.listarCategorias();
        int total = servico.listarTodas().size();

        listaCategorias.getChildren().addAll(
                itemDeFiltro(Icone.Simbolo.ORDENAR, "Todas as notas", total, FILTRO_TODAS),
                itemDeFiltro(Icone.Simbolo.ESTRELA, "Favoritas", servico.listarFavoritas().size(), FILTRO_FAVORITAS));

        Region separador = new Region();
        separador.getStyleClass().add("separador");
        VBox.setMargin(separador, new Insets(8, 6, 8, 6));
        listaCategorias.getChildren().addAll(separador, listaSoCategorias);

        listaSoCategorias.getChildren().clear();
        for (Categoria categoria : categorias) {
            listaSoCategorias.getChildren().add(itemDeCategoria(categoria));
        }

        // A ordem das categorias é sempre a sua — não há ordenação automática
        // para a qual voltar, então o arrasto vale o tempo todo.
        Arrastavel.instalar(listaSoCategorias, categorias.size(), (de, para) -> {
            Arrastavel.mover(categorias, de, para);
            servico.reordenarCategorias(categorias);
        });

        int semCategoria = servico.listarSemCategoria().size();
        if (semCategoria > 0) {
            listaCategorias.getChildren().add(
                    itemDeFiltro(Icone.Simbolo.BAIXAR, "Sem categoria", semCategoria, FILTRO_SEM_CATEGORIA));
        }

        Region separador2 = new Region();
        separador2.getStyleClass().add("separador");
        VBox.setMargin(separador2, new Insets(8, 6, 8, 6));
        listaCategorias.getChildren().addAll(
                separador2,
                itemDeFiltro(Icone.Simbolo.EXCLUIR, "Lixeira", servico.listarExcluidas().size(), FILTRO_LIXEIRA));
    }

    private HBox itemDeFiltro(Icone.Simbolo icone, String nome, int quantidade, String marcador) {
        Label rotulo = new Label(nome);
        rotulo.setGraphic(Icone.de(icone, 16));
        rotulo.setGraphicTextGap(10);
        rotulo.getStyleClass().add("item-categoria-texto");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Label numero = new Label(String.valueOf(quantidade));
        numero.getStyleClass().add("item-categoria-contagem");

        HBox item = new HBox(6, rotulo, espaco, numero);
        item.getStyleClass().add("item-categoria");
        item.setAlignment(Pos.CENTER_LEFT);
        if (marcador.equals(filtroAtual)) {
            item.getStyleClass().add("ativo");
        }
        item.setOnMouseClicked(e -> {
            filtroAtual = marcador;
            recarregarCategorias();
            recarregarNotas();
        });
        return item;
    }

    private HBox itemDeCategoria(Categoria categoria) {
        Region cor = new Region();
        cor.getStyleClass().add("ponto-categoria");
        cor.setStyle("-fx-background-color: " + corSegura(categoria.getCor()) + ";");

        Label rotulo = new Label(categoria.getNome());
        rotulo.setGraphic(Icone.deCategoria(categoria.getIcone(), 16));
        rotulo.setGraphicTextGap(8);
        rotulo.getStyleClass().add("item-categoria-texto");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Label numero = new Label(String.valueOf(categoria.getQuantidadeDeNotas()));
        numero.getStyleClass().add("item-categoria-contagem");

        HBox item = new HBox(8, cor, rotulo, espaco, numero);
        item.getStyleClass().add("item-categoria");
        item.setAlignment(Pos.CENTER_LEFT);

        if (categoria.getId().equals(filtroAtual)) {
            item.getStyleClass().add("ativo");
        }

        item.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                abrirEditorDeCategoria(categoria);
                return;
            }
            filtroAtual = categoria.getId();
            recarregarCategorias();
            recarregarNotas();
        });

        Botoes.instalarDica(item, categoria.getNome()
                + " — clique duas vezes para renomear ou excluir, "
                + "ou arraste para mudar de lugar");
        return item;
    }

    // ----------------------------------------------------- coluna 2: notas

    private VBox montarColunaNotas() {
        busca.textProperty().addListener((o, a, n) -> recarregarNotas());

        Button nova = Botoes.primario("Nova nota", Icone.Simbolo.ADICIONAR);
        nova.setOnAction(e -> abrirEditor(null));

        ordenacao.getItems().addAll("Recentes", "Minha ordem");
        ordenacao.setValue(servico.temOrdemManual() ? "Minha ordem" : "Recentes");
        ordenacao.getStyleClass().add("campo");
        ordenacao.setTooltip(dica("Em \"Minha ordem\" você arrasta os cartões para onde quiser"));
        ordenacao.setOnAction(e -> recarregarNotas());

        // Só aparece quando a coluna de categorias está escondida — por falta
        // de espaço ou por escolha de quem usa.
        botaoCategorias = Botoes.icone(Icone.Simbolo.ORDENAR, "Mostrar ou esconder as categorias");
        botaoCategorias.setVisible(false);
        botaoCategorias.setManaged(false);
        botaoCategorias.setOnAction(e -> {
            escondidaPeloUsuario = colunaCategorias.isVisible();
            mostrarCategorias(!colunaCategorias.isVisible());
        });

        // Duas linhas: as ações em cima, a busca inteira embaixo.
        //
        //   [+ Nova nota] [Minha ordem     ▾]
        //   [≡] [ Buscar nas notas...     ✕ ]
        //
        // Na mesma linha da busca o botão era o primeiro a ceder espaço e
        // virava "+ Nova n...". Agora ele nunca encolhe abaixo do próprio
        // texto; quem se ajusta é a ordenação, que sobra à direita. O botão
        // das categorias desceu para a linha da busca: na de cima, com a
        // janela no tamanho padrão, ele cortava o "Recentes".
        nova.setMinWidth(Region.USE_PREF_SIZE);
        botaoCategorias.setMinWidth(Region.USE_PREF_SIZE);
        ordenacao.setMaxWidth(Double.MAX_VALUE);
        ordenacao.setMinWidth(110);
        HBox.setHgrow(ordenacao, Priority.ALWAYS);

        HBox linhaAcoes = new HBox(10, nova, ordenacao);
        linhaAcoes.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(busca, Priority.ALWAYS);
        HBox linhaBusca = new HBox(10, botaoCategorias, busca);
        linhaBusca.setAlignment(Pos.CENTER_LEFT);

        contador.getStyleClass().add("subtitulo");

        VBox cabecalho = new VBox(10, linhaAcoes, linhaBusca, contador);
        cabecalho.setPadding(new Insets(0, 0, 12, 0));

        listaNotas.setPadding(new Insets(0, 8, 16, 0));
        ScrollPane rolagem = new ScrollPane(listaNotas);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        VBox coluna = new VBox(0, cabecalho, rolagem);
        coluna.getStyleClass().add("coluna-notas");
        return coluna;
    }

    private void recarregarNotas() {
        listaNotas.getChildren().clear();

        List<Nota> universo = universoAtual();
        boolean ordemManual = "Minha ordem".equals(ordenacao.getValue())
                && !FILTRO_LIXEIRA.equals(filtroAtual);

        List<Nota> encontradas = servico.buscar(busca.getText(), universo);
        if (ordemManual) {
            encontradas = servico.naMinhaOrdem(encontradas);
        }
        List<Nota> visiveis = new java.util.ArrayList<>(encontradas);

        contador.setText(descreverContagem(universo.size(), visiveis.size())
                + (ordemManual ? "  •  arraste para reordenar" : ""));

        if (visiveis.isEmpty()) {
            listaNotas.getChildren().add(estadoVazio(universo.isEmpty()));
            return;
        }
        for (Nota nota : visiveis) {
            listaNotas.getChildren().add(cartaoDeNota(nota));
        }

        // Arrastar só vale na ordem manual: numa lista ordenada por alteração,
        // o arranjo seria desfeito no próximo desenho.
        if (ordemManual) {
            List<Nota> paraMover = visiveis;
            Arrastavel.instalar(listaNotas, paraMover.size(), (de, para) -> {
                Arrastavel.mover(paraMover, de, para);
                servico.reordenar(paraMover);
            });
        }
    }

    private List<Nota> universoAtual() {
        if (filtroAtual instanceof Long id) {
            return servico.listarPorCategoria(id);
        }
        return switch (String.valueOf(filtroAtual)) {
            case FILTRO_FAVORITAS -> servico.listarFavoritas();
            case FILTRO_SEM_CATEGORIA -> servico.listarSemCategoria();
            case FILTRO_LIXEIRA -> servico.listarExcluidas();
            default -> servico.listarTodas();
        };
    }

    private String descreverContagem(int total, int visiveis) {
        if (total == 0) {
            return FILTRO_LIXEIRA.equals(filtroAtual)
                    ? "0 notas na lixeira"
                    : "0 notas";
        }
        if (visiveis == total) {
            return total + (total == 1 ? " nota" : " notas");
        }
        return visiveis + " de " + total + " notas";
    }

    private VBox cartaoDeNota(Nota nota) {
        Node icone = Icone.de(nota.getTipo().icone(), 16, "cartao-nota-icone");

        Label titulo = new Label(nota.getTitulo());
        titulo.getStyleClass().add("cartao-nota-titulo");
        titulo.setWrapText(true);

        HBox linhaTitulo = new HBox(8, icone, titulo);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);

        // As marcas de estado ficam depois do título, todas do mesmo tamanho,
        // para a linha continuar legível quando a nota tem as três.
        if (nota.isFixada()) {
            linhaTitulo.getChildren().add(
                    marca(Icone.Simbolo.FIXAR, "icone-primario", "Fixada no topo"));
        }
        if (nota.isFavorita()) {
            linhaTitulo.getChildren().add(
                    marca(Icone.Simbolo.ESTRELA_CHEIA, "icone-atencao", "Favorita"));
        }
        if (nota.isProtegida()) {
            linhaTitulo.getChildren().add(
                    marca(Icone.Simbolo.CADEADO, "icone-atencao", "Conteúdo cifrado"));
        }

        Label resumo = new Label(nota.resumo());
        resumo.getStyleClass().add("cartao-nota-resumo");
        resumo.setWrapText(true);

        VBox cartao = new VBox(4, linhaTitulo, resumo);
        cartao.getStyleClass().add("cartao-nota");

        if (notaAberta != null && notaAberta.getId() != null
                && notaAberta.getId().equals(nota.getId())) {
            cartao.getStyleClass().add("ativo");
        }

        cartao.setOnMouseClicked(e -> {
            abrirDetalhe(nota);
            recarregarNotas();
            if (e.getClickCount() == 2 && !nota.isExcluida()) {
                abrirEditor(nota);
            }
        });
        return cartao;
    }

    /** Uma marca de estado no cartão: fixada, favorita, protegida. */
    private Node marca(Icone.Simbolo simbolo, String cor, String explicacao) {
        Node no = Icone.de(simbolo, 14, cor);
        Botoes.instalarDica(no, explicacao);
        return no;
    }

    private EstadoVazio estadoVazio(boolean semNada) {
        if (!semNada) {
            return new EstadoVazio(Icone.Simbolo.BUSCAR, "Nada corresponde à busca.")
                    .comExplicacao("A busca olha o título, o texto e os campos comuns — "
                            + "nunca as senhas.");
        }
        if (FILTRO_LIXEIRA.equals(filtroAtual)) {
            return new EstadoVazio(Icone.Simbolo.EXCLUIR, "A lixeira está vazia.")
                    .comExplicacao("O que você excluir vem para cá e pode ser restaurado.");
        }
        return new EstadoVazio(Icone.Simbolo.NOTA, "Nenhuma nota aqui ainda.")
                .comAcao("Criar a primeira nota", () -> abrirEditor(null));
    }

    // -------------------------------------------------- coluna 3: detalhe

    private ScrollPane montarColunaDetalhe() {
        painelDetalhe.setPadding(new Insets(4, 4, 16, 20));

        ScrollPane rolagem = new ScrollPane(painelDetalhe);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().addAll("rolagem-formulario", "coluna-detalhe");
        mostrarNadaSelecionado();
        return rolagem;
    }

    private void mostrarNadaSelecionado() {
        painelDetalhe.getChildren().clear();

        painelDetalhe.getChildren().add(
                new EstadoVazio(Icone.Simbolo.NOTA, "Escolha uma nota para ver o conteúdo.")
                        .comExplicacao("Um clique abre aqui; dois cliques abrem para editar."));
    }

    private void abrirDetalhe(Nota nota) {
        this.notaAberta = nota;
        painelDetalhe.getChildren().clear();

        // ---------- cabeçalho ----------
        Label titulo = new Label(nota.getTitulo());
        titulo.getStyleClass().add("titulo-tela");
        titulo.setWrapText(true);

        Label subtitulo = new Label(nota.getTipo().rotulo()
                + "  •  alterada em " + DATA_HORA.format(nota.getAtualizadoEm()));
        subtitulo.getStyleClass().add("subtitulo");

        painelDetalhe.getChildren().addAll(titulo, subtitulo, montarAcoes(nota));

        // ---------- campos ----------
        if (!nota.getCampos().isEmpty()) {
            VBox campos = new VBox(12);
            for (CampoNota campo : nota.getCampos()) {
                if (campo.estaVazio()) {
                    continue;
                }
                campos.getChildren().add(campo.isSensivel()
                        ? linhaDeSegredo(campo)
                        : linhaDeCampo(campo));
            }
            if (!campos.getChildren().isEmpty()) {
                painelDetalhe.getChildren().add(new Secao(Icone.Simbolo.ORDENAR, "DADOS", campos));
            }
        }

        // ---------- corpo ----------
        if (nota.getCorpo() != null && !nota.getCorpo().isBlank()) {
            painelDetalhe.getChildren().add(montarCorpo(nota));
        }

    }

    /**
     * A linha de ações da nota aberta.
     *
     * <p>É um {@code FlowPane}, e não um {@code HBox}, porque são até sete
     * botões numa coluna que encolhe: em caixa estreita o HBox simplesmente
     * cortava os últimos e eles desapareciam da tela. Quebrando a linha, o
     * pior caso é ocupar duas alturas — nunca perder um botão.
     */
    private FlowPane montarAcoes(Nota nota) {
        FlowPane acoes = new FlowPane(8, 8);
        acoes.setAlignment(Pos.CENTER_LEFT);
        acoes.setPadding(new Insets(6, 0, 4, 0));

        if (nota.isExcluida()) {
            Button restaurar = Botoes.primario("Restaurar", Icone.Simbolo.RESTAURAR);
            restaurar.setOnAction(e -> {
                servico.restaurar(nota.getId());
                Aviso.sucesso("Nota restaurada.");
                mostrarNadaSelecionado();
            });
            acoes.getChildren().add(restaurar);
            return acoes;
        }

        Button editar = Botoes.primario("Editar", Icone.Simbolo.EDITAR);
        editar.setOnAction(e -> abrirEditor(nota));
        acoes.getChildren().add(editar);

        if (nota.getTipo() == TipoNota.LINK) {
            Button abrir = Botoes.comum("Abrir", Icone.Simbolo.ABRIR_FORA);
            abrir.setOnAction(e -> {
                try {
                    servico.abrirLink(nota);
                } catch (Exception erro) {
                    Dialogos.erro(getScene().getWindow(), "Não foi possível abrir",
                            erro.getMessage());
                }
            });
            acoes.getChildren().add(abrir);
        }

        if (nota.getTipo() == TipoNota.CREDENCIAL && nota.valor("Endereço").isPresent()) {
            Button abrir = Botoes.comum("Abrir endereço", Icone.Simbolo.ABRIR_FORA);
            abrir.setOnAction(e -> servico.abrirEnderecoDaCredencial(nota));
            acoes.getChildren().add(abrir);
        }

        Button favorita = Botoes.icone(
                nota.isFavorita() ? Icone.Simbolo.ESTRELA_CHEIA : Icone.Simbolo.ESTRELA,
                nota.isFavorita() ? "Tirar dos favoritos" : "Marcar como favorita");
        favorita.setOnAction(e -> {
            servico.alternarFavorita(nota);
            abrirDetalhe(nota);
            recarregarCategorias();
        });

        // Ligado, o botão fica azul — o mesmo desenho nos dois estados não
        // dizia se a nota estava fixada ou não.
        Button fixar = Botoes.icone(Icone.Simbolo.FIXAR, nota.isFixada()
                ? "Fixada no topo — clique para soltar"
                : "Fixar no topo da lista, acima das outras, em qualquer ordenação");
        if (nota.isFixada()) {
            fixar.getStyleClass().add("ligado");
        }
        fixar.setOnAction(e -> {
            servico.alternarFixada(nota);
            Aviso.sucesso(nota.isFixada()
                    ? "Nota fixada no topo da lista."
                    : "Nota solta — voltou ao lugar de sempre.");
            abrirDetalhe(nota);
        });

        Button duplicar = Botoes.icone(Icone.Simbolo.DUPLICAR,
                "Duplicar — cria uma cópia para você ajustar");
        duplicar.setOnAction(e -> {
            servico.duplicar(nota);
            Aviso.sucesso("Cópia criada.");
        });

        Button excluir = Botoes.iconePerigo(Icone.Simbolo.EXCLUIR,
                "Excluir — vai para a lixeira e pode ser restaurada");
        excluir.setOnAction(e -> confirmarExclusao(nota));

        acoes.getChildren().addAll(favorita, fixar, duplicar, excluir);
        return acoes;
    }

    private VBox linhaDeCampo(CampoNota campo) {
        TextField valor = new TextField(campo.getValor());
        valor.getStyleClass().add("campo");
        valor.setEditable(false);
        HBox.setHgrow(valor, Priority.ALWAYS);

        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR, "Copiar");
        copiar.setOnAction(e -> {
            AreaTransferencia.copiar(campo.getValor());
            Aviso.info(campo.getChave() + " copiado.");
        });

        HBox linha = new HBox(8, valor, copiar);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, Secao.campo(campo.getChave()), linha);
    }

    /**
     * Um segredo no painel de leitura.
     *
     * Nasce oculto. Revelar é um clique consciente, e copiar avisa que o valor
     * será apagado da área de transferência em alguns segundos.
     */
    private VBox linhaDeSegredo(CampoNota campo) {
        boolean legivel = !NotaDao.SEGREDO_OCULTO.equals(campo.getValor());

        TextField valor = new TextField(legivel
                ? "••••••••••••"
                : "destranque o aplicativo para ver");
        valor.getStyleClass().add("campo");
        valor.setEditable(false);
        HBox.setHgrow(valor, Priority.ALWAYS);

        Button ver = Botoes.icone(Icone.Simbolo.OLHO, "Mostrar ou esconder");
        ver.setDisable(!legivel);
        ver.setOnAction(e -> {
            boolean escondido = valor.getText().startsWith("•");
            valor.setText(escondido ? campo.getValor() : "••••••••••••");
            Botoes.trocarIcone(ver, escondido ? Icone.Simbolo.OLHO_FECHADO : Icone.Simbolo.OLHO);
        });

        Button copiar = Botoes.icone(Icone.Simbolo.COPIAR,
                "Copiar — some da área de transferência em "
                        + AreaTransferencia.SEGUNDOS_ATE_LIMPAR + " segundos");
        copiar.setDisable(!legivel);
        copiar.setOnAction(e -> {
            AreaTransferencia.copiarSegredo(campo.getValor());
            avisarCopia(copiar);
        });

        HBox linha = new HBox(8, valor, ver, copiar);
        linha.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, Secao.campo(campo.getChave()), linha);
    }

    /**
     * Confirma a cópia de um segredo.
     *
     * O ícone vira um visto por um instante — a confirmação aparece embaixo do
     * dedo, onde o olho já está — e o aviso no canto lembra que o valor não
     * fica na área de transferência para sempre.
     */
    private void avisarCopia(Button botao) {
        Botoes.trocarIcone(botao, Icone.Simbolo.CONFIRMAR);
        Aviso.sucesso("Senha copiada — some da área de transferência em "
                + AreaTransferencia.SEGUNDOS_ATE_LIMPAR + " s.");

        javafx.animation.PauseTransition pausa =
                new javafx.animation.PauseTransition(Duration.seconds(1.4));
        pausa.setOnFinished(e -> Botoes.trocarIcone(botao, Icone.Simbolo.COPIAR));
        pausa.play();
    }

    /**
     * O corpo da nota, com o "Copiar tudo" no cabeçalho do próprio bloco.
     *
     * <p>Os dois formatos deixam selecionar um trecho com o mouse e copiar
     * com Ctrl+C; o botão é o atalho para o caso mais comum, o texto inteiro.
     */
    private Secao montarCorpo(Nota nota) {
        boolean ehCodigo = nota.getTipo() == TipoNota.CODIGO;

        Button copiarTudo = Botoes.miudo("Copiar tudo", Icone.Simbolo.COPIAR);
        Botoes.instalarDica(copiarTudo, "Para copiar só uma parte, selecione com o mouse e use Ctrl+C");
        copiarTudo.setOnAction(e -> {
            AreaTransferencia.copiar(nota.getCorpo());
            Aviso.info(ehCodigo ? "Código copiado." : "Texto copiado.");
        });

        if (ehCodigo) {
            String linguagem = nota.valor("Linguagem").orElse("Texto");
            TextoSelecionavel codigo = new TextoSelecionavel(
                    DestacadorSintaxe.destacar(nota.getCorpo(), linguagem), nota.getCorpo());
            codigo.getStyleClass().add("bloco-codigo");
            return new Secao(Icone.Simbolo.CODIGO, "CÓDIGO", codigo).comAcoes(copiarTudo);
        }

        TextArea texto = new TextArea(nota.getCorpo());
        texto.getStyleClass().add("campo");
        texto.setEditable(false);
        texto.setWrapText(true);
        texto.setPrefRowCount(Math.min(20, Math.max(4, contarLinhas(nota.getCorpo()) + 1)));

        return new Secao(Icone.Simbolo.TEXTO, nota.getTipo().rotuloDoCorpo(), texto)
                .comAcoes(copiarTudo);
    }

    private int contarLinhas(String texto) {
        return texto == null ? 0 : texto.split("\r?\n", -1).length;
    }

    // -------------------------------------------------------------- ações

    private void abrirEditor(Nota nota) {
        if (nota != null && nota.isProtegida() && !SecurityService.estaDestrancado()) {
            Dialogos.info(getScene().getWindow(), "Nota protegida",
                    "Destranque o aplicativo para editar esta nota.");
            return;
        }
        // Nota nova criada de dentro de uma categoria já nasce nela. Os outros
        // filtros (todas, favoritas...) não são categorias e não sugerem nada.
        Long categoriaAberta = filtroAtual instanceof Long id ? id : null;
        EditorNota editor = new EditorNota(getScene().getWindow(), nota, servico,
                servico.listarCategorias(), categoriaAberta);

        boolean ehNova = nota == null;
        editor.abrir().ifPresent(preenchida -> {
            try {
                Nota salva = servico.salvar(preenchida);
                Aviso.sucesso(ehNova ? "Nota criada com sucesso!" : "Nota salva.");
                abrirDetalhe(salva);
                recarregarNotas();
            } catch (IllegalArgumentException e) {
                Dialogos.erro(getScene().getWindow(), "Não foi possível salvar", e.getMessage());
            } catch (IllegalStateException e) {
                Dialogos.erro(getScene().getWindow(), "Conteúdo protegido", e.getMessage());
            }
        });
    }

    private void confirmarExclusao(Nota nota) {
        boolean confirmou = Dialogos.confirmarExclusao(getScene().getWindow(),
                "a nota \"" + nota.getTitulo() + "\"",
                "Ela sai da lista, mas continua guardada. "
                        + "Para trazê-la de volta, use a Lixeira.");
        if (confirmou) {
            servico.excluir(nota.getId());
            Aviso.sucesso("Nota movida para a lixeira.");
            mostrarNadaSelecionado();
            notaAberta = null;
        }
    }

    private void abrirEditorDeCategoria(Categoria categoria) {
        new DialogoCategoria(getScene().getWindow(), categoria, servico).abrir();
    }

    // -------------------------------------------------------------- apoio

    private Tooltip dica(String texto) {
        return Botoes.dica(texto);
    }

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

    /** Redesenha ao trancar ou destrancar: o conteúdo protegido muda. */
    public void aoMudarBloqueio() {
        notaAberta = null;
        mostrarNadaSelecionado();
        recarregar();
    }
}
