package br.com.myapp.modules.inicio;

import br.com.myapp.modules.lembretes.TipoRecorrencia;
import br.com.myapp.security.SecurityService;
import br.com.myapp.ui.Botoes;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * As grades de semana, mês e ano da agenda da tela inicial.
 *
 * Só desenho: os dados chegam prontos, separados por dia
 * ({@link InicioService#porDia}), e cada clique é devolvido a quem montou a
 * grade pelas {@link Acoes}. A semana começa no domingo, como no calendário
 * do Windows.
 *
 * <pre>
 *   Semana: 7 colunas, um cartãozinho por compromisso
 *   Mês:    a folhinha, até 3 por dia e "+2 mais"
 *   Ano:    12 folhinhas pequenas, o dia pintado conforme o movimento
 * </pre>
 */
final class CalendarioGrade {

    /** O que a grade pede para quem a montou. */
    interface Acoes {
        void abrir(InicioService.CompromissoDoDia item);

        void irParaDia(LocalDate dia);

        void irParaMes(LocalDate diaDoMes);
    }

    static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** No mês, quantos compromissos cabem num dia antes do "+N mais". */
    private static final int ITENS_POR_DIA_NO_MES = 3;

    private CalendarioGrade() {
    }

    /** O domingo da semana de um dia — a semana começa no domingo, como no calendário do Windows. */
    static LocalDate inicioDaSemana(LocalDate dia) {
        return dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    // ---------------------------------------------------------------- semana

    static Node semana(LocalDate qualquerDia, Map<LocalDate, List<InicioService.CompromissoDoDia>> porDia,
                       Acoes acoes) {
        LocalDate domingo = inicioDaSemana(qualquerDia);
        GridPane grade = gradeDeSeteColunas(8);

        for (int i = 0; i < 7; i++) {
            LocalDate dia = domingo.plusDays(i);

            Label nome = new Label(dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR)
                    .replace(".", "").toUpperCase());
            nome.getStyleClass().add("semana-dia-nome");
            Label numero = new Label(String.valueOf(dia.getDayOfMonth()));
            numero.getStyleClass().add("semana-dia-numero");
            if (dia.equals(LocalDate.now())) {
                numero.getStyleClass().add("hoje");
            }
            VBox cabeca = new VBox(2, nome, numero);
            cabeca.setAlignment(Pos.CENTER);
            cabeca.getStyleClass().add("semana-cabeca");
            clicavel(cabeca, () -> acoes.irParaDia(dia));
            Botoes.instalarDica(cabeca, "Ver o dia");

            VBox coluna = new VBox(6, cabeca);
            coluna.getStyleClass().add("semana-coluna");
            if (dia.equals(LocalDate.now())) {
                coluna.getStyleClass().add("hoje");
            }
            for (InicioService.CompromissoDoDia item : porDia.getOrDefault(dia, List.of())) {
                coluna.getChildren().add(item(item, true, acoes));
            }
            grade.add(coluna, i, 0);
        }
        return grade;
    }

    // ------------------------------------------------------------------- mês

    static Node mes(LocalDate qualquerDia, Map<LocalDate, List<InicioService.CompromissoDoDia>> porDia,
                    Acoes acoes) {
        YearMonth mes = YearMonth.from(qualquerDia);
        LocalDate primeiro = inicioDaSemana(mes.atDay(1));
        LocalDate ultimo = mes.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        GridPane grade = gradeDeSeteColunas(6);
        cabecalhoDosDias(grade, false);

        int linha = 1;
        int coluna = 0;
        for (LocalDate dia = primeiro; !dia.isAfter(ultimo); dia = dia.plusDays(1)) {
            grade.add(celulaDoMes(dia, mes, porDia.getOrDefault(dia, List.of()), acoes), coluna, linha);
            if (++coluna == 7) {
                coluna = 0;
                linha++;
            }
        }
        return grade;
    }

    private static Node celulaDoMes(LocalDate dia, YearMonth mes, List<InicioService.CompromissoDoDia> itens,
                                    Acoes acoes) {
        Label numero = new Label(String.valueOf(dia.getDayOfMonth()));
        numero.getStyleClass().add("mes-dia-numero");
        if (dia.equals(LocalDate.now())) {
            numero.getStyleClass().add("hoje");
        }
        clicavel(numero, () -> acoes.irParaDia(dia));

        VBox celula = new VBox(3, numero);
        celula.getStyleClass().add("mes-celula");
        if (!YearMonth.from(dia).equals(mes)) {
            celula.getStyleClass().add("fora-do-mes");
        }
        if (dia.equals(LocalDate.now())) {
            celula.getStyleClass().add("hoje");
        }

        itens.stream().limit(ITENS_POR_DIA_NO_MES)
                .forEach(item -> celula.getChildren().add(item(item, false, acoes)));
        if (itens.size() > ITENS_POR_DIA_NO_MES) {
            Label mais = new Label("+" + (itens.size() - ITENS_POR_DIA_NO_MES) + " mais");
            mais.getStyleClass().add("mes-mais");
            clicavel(mais, () -> acoes.irParaDia(dia));
            celula.getChildren().add(mais);
        }
        return celula;
    }

    // ------------------------------------------------------------------- ano

    static Node ano(int ano, Map<LocalDate, List<InicioService.CompromissoDoDia>> porDia, Acoes acoes) {
        GridPane grade = new GridPane();
        grade.setHgap(18);
        grade.setVgap(18);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25);
            grade.getColumnConstraints().add(c);
        }
        for (int m = 1; m <= 12; m++) {
            grade.add(folhinha(YearMonth.of(ano, m), porDia, acoes), (m - 1) % 4, (m - 1) / 4);
        }
        return grade;
    }

    /** Um mês pequeno: o dia com compromisso ganha cor, mais forte quanto mais cheio. */
    private static Node folhinha(YearMonth mes, Map<LocalDate, List<InicioService.CompromissoDoDia>> porDia,
                                 Acoes acoes) {
        Label nome = new Label(maiuscula(mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR)));
        nome.getStyleClass().add("ano-mes-nome");
        clicavel(nome, () -> acoes.irParaMes(mes.atDay(1)));
        Botoes.instalarDica(nome, "Ver o mês");

        GridPane dias = gradeDeSeteColunas(2);
        cabecalhoDosDias(dias, true);

        LocalDate primeiro = inicioDaSemana(mes.atDay(1));
        int linha = 1;
        int coluna = 0;
        for (LocalDate dia = primeiro; !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            if (YearMonth.from(dia).equals(mes)) {
                dias.add(diaDaFolhinha(dia, porDia.getOrDefault(dia, List.of()).size(), acoes), coluna, linha);
            }
            if (++coluna == 7) {
                coluna = 0;
                linha++;
            }
        }

        VBox folhinha = new VBox(8, nome, dias);
        folhinha.getStyleClass().add("ano-folhinha");
        return folhinha;
    }

    private static Node diaDaFolhinha(LocalDate dia, int quantos, Acoes acoes) {
        Label numero = new Label(String.valueOf(dia.getDayOfMonth()));
        numero.getStyleClass().add("ano-dia");
        numero.setMaxWidth(Double.MAX_VALUE);
        numero.setAlignment(Pos.CENTER);
        if (quantos > 0) {
            numero.getStyleClass().add(quantos == 1 ? "ano-dia-1" : quantos <= 3 ? "ano-dia-2" : "ano-dia-3");
            Botoes.instalarDica(numero, quantos == 1 ? "1 compromisso" : quantos + " compromissos");
        }
        if (dia.equals(LocalDate.now())) {
            numero.getStyleClass().add("hoje");
        }
        clicavel(numero, () -> acoes.irParaDia(dia));
        return numero;
    }

    // ---------------------------------------------------------------- peças

    /**
     * Um compromisso em miniatura: a cor de quem é, a hora e o título.
     * O título inteiro aparece ao parar o mouse; o clique abre o detalhe.
     */
    static Node item(InicioService.CompromissoDoDia item, boolean naSemana, Acoes acoes) {
        boolean protegido = item.protegido() && !SecurityService.estaDestrancado();
        String titulo = protegido ? (item.ehDaAgenda() ? "Evento protegido" : "Lembrete protegido")
                : item.titulo();

        String hora = item.diaInteiro() ? "" : HORA.format(item.quando()) + "  ";
        String repete = !item.ehDaAgenda() && item.lembrete().getTipo() == TipoRecorrencia.INTERVALO
                ? "  ↻" : "";

        Label texto = new Label(hora + titulo + repete);
        texto.getStyleClass().add("item-calendario-texto");
        texto.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texto, Priority.ALWAYS);

        Region faixa = new Region();
        faixa.getStyleClass().add("item-calendario-faixa");
        faixa.setStyle("-fx-background-color: " + item.cor() + ";");

        HBox caixa = new HBox(6, faixa, texto);
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.getStyleClass().add("item-calendario");
        if (naSemana) {
            caixa.getStyleClass().add("na-semana");
        }
        if (item.diaInteiro()) {
            caixa.getStyleClass().add("dia-inteiro");
        }
        if (item.jaPassou()) {
            caixa.getStyleClass().add("passou");
        }

        StringBuilder dica = new StringBuilder(titulo);
        dica.append("\n").append(item.diaInteiro() ? "Dia inteiro" : HORA.format(item.quando())
                + (item.fim() != null && item.fim().isAfter(item.quando())
                && item.fim().toLocalDate().equals(item.quando().toLocalDate())
                ? " – " + HORA.format(item.fim()) : ""));
        if (!repete.isEmpty()) {
            dica.append(" · a cada ").append(item.lembrete().getIntervaloMinutos()).append(" min");
        }
        dica.append(item.ehDaAgenda() ? "\nGoogle Agenda · " + item.evento().agenda().getNome() : "\nLembrete");
        Botoes.instalarDica(caixa, dica.toString());

        clicavel(caixa, () -> acoes.abrir(item));
        return caixa;
    }

    private static GridPane gradeDeSeteColunas(double espaco) {
        GridPane grade = new GridPane();
        grade.setHgap(espaco);
        grade.setVgap(espaco);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / 7);
            c.setFillWidth(true);
            grade.getColumnConstraints().add(c);
        }
        return grade;
    }

    /** DOM SEG TER… na primeira linha; na folhinha do ano, só a inicial. */
    private static void cabecalhoDosDias(GridPane grade, boolean soInicial) {
        for (int i = 0; i < 7; i++) {
            String nome = DayOfWeek.SUNDAY.plus(i).getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "").toUpperCase();
            Label rotulo = new Label(soInicial ? nome.substring(0, 1) : nome);
            rotulo.getStyleClass().add(soInicial ? "ano-dia-semana" : "mes-dia-semana");
            rotulo.setMaxWidth(Double.MAX_VALUE);
            rotulo.setAlignment(Pos.CENTER);
            grade.add(rotulo, i, 0);
        }
    }

    private static void clicavel(Node no, Runnable acao) {
        no.setCursor(Cursor.HAND);
        no.setOnMouseClicked(e -> {
            acao.run();
            e.consume();
        });
    }

    static String maiuscula(String texto) {
        return texto == null || texto.isEmpty() ? texto
                : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
