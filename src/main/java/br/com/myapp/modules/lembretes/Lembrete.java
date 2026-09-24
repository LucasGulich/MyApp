package br.com.myapp.modules.lembretes;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Um lembrete da agenda.
 *
 * O campo {@code inicio} guarda a data e a hora do evento em si. Os avisos
 * saem antes disso, conforme a lista de antecedências - o Lembrete.bat tinha
 * um único aviso fixo de 5 minutos; aqui você pode pedir, por exemplo, um
 * aviso um dia antes, outro uma hora antes e outro na hora exata.
 */
public class Lembrete {

    private Long id;
    private String titulo = "";
    private String descricao = "";

    /** Conteúdo protegido: Título e descrição ficam cifrados no banco. */
    private boolean sensivel;

    private TipoRecorrencia tipo = TipoRecorrencia.UNICO;

    /** Data e hora do evento (a primeira ocorrência, no caso dos repetidos). */
    private LocalDateTime inicio = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

    /** Data limite da repetição. Nulo significa "para sempre". */
    private LocalDateTime fim;

    /** Usado por SEMANAL. */
    private Set<DayOfWeek> diasSemana = new LinkedHashSet<>();

    /** Usado por MENSAL: 1 a 31. Meses curtos usam o último dia disponível. */
    private Integer diaMes;

    /** Usado por INTERVALO. */
    private Integer intervaloMinutos;

    /** Minutos de antecedência de cada aviso. 0 significa "na hora exata". */
    private List<Integer> antecedencias = new ArrayList<>(List.of(5));

    private boolean ativo = true;

    /** Posição escolhida ao arrastar. -1 = nunca foi arrastado. */
    private int ordem = -1;

    /** Tocar som quando este lembrete alertar. */
    private boolean somAtivo = true;

    /**
     * Quando o lembrete foi excluído.
     *
     * Nulo significa que ele está valendo. Preenchido, o lembrete some das
     * telas e para de alertar, mas continua no banco e pode ser restaurado.
     */
    private LocalDateTime dataExclusao;

    /** Cor da etiqueta na lista, em hexadecimal. */
    private String cor = "#4C8DFF";

    /** Opcional: link, caminho de pasta ou comando aberto pelo botao do alerta. */
    private String acao = "";

    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    // ------------------------------------------------------- conversoes CSV

    /** Dias da semana no formato gravado no banco: "MONDAY,WEDNESDAY". */
    public String diasSemanaCsv() {
        return diasSemana.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public void diasSemanaCsv(String csv) {
        diasSemana = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String parte : csv.split(",")) {
            String limpo = parte.trim();
            if (!limpo.isEmpty()) {
                diasSemana.add(DayOfWeek.valueOf(limpo));
            }
        }
    }

    /** Antecedências no formato gravado no banco: "0,5,60". */
    public String antecedenciasCsv() {
        return antecedencias.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public void antecedenciasCsv(String csv) {
        antecedencias = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            antecedencias.add(0);
            return;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .distinct()
                .sorted()
                .forEach(antecedencias::add);
        if (antecedencias.isEmpty()) {
            antecedencias.add(0);
        }
    }

    // -------------------------------------------------------------- Validação

    /**
     * Verifica se o lembrete esta preenchido de forma coerente.
     *
     * @return mensagem de erro, ou nulo se estiver tudo certo
     */
    public String validar() {
        if (titulo == null || titulo.isBlank()) {
            return "Escreva um título para o lembrete.";
        }
        if (inicio == null) {
            return "Informe a data e a hora.";
        }
        if (fim != null && fim.isBefore(inicio)) {
            return "A data de término não pode ser anterior à data de início.";
        }
        if (tipo.usaDiasDaSemana() && diasSemana.isEmpty()) {
            return "Escolha pelo menos um dia da semana.";
        }
        if (tipo.usaDiaDoMes() && (diaMes == null || diaMes < 1 || diaMes > 31)) {
            return "Informe um dia do mês entre 1 e 31.";
        }
        if (tipo.usaIntervalo() && (intervaloMinutos == null || intervaloMinutos < 1)) {
            return "Informe o intervalo em minutos (no mínimo 1).";
        }
        if (antecedencias.stream().anyMatch(m -> m < 0)) {
            return "A antecedência do aviso não pode ser negativa.";
        }
        return null;
    }

    /** Descrição curta da repetição, mostrada na lista. */
    public String resumoRecorrencia() {
        return switch (tipo) {
            case UNICO -> "Uma vez";
            case DIARIO -> "Todo dia";
            case DIAS_UTEIS -> "Dias úteis";
            case SEMANAL -> diasSemana.stream().map(Lembrete::abreviar).collect(Collectors.joining(", "));
            case MENSAL -> "Todo dia " + diaMes;
            case ANUAL -> "Todo ano em " + inicio.getDayOfMonth() + "/" + inicio.getMonthValue();
            case INTERVALO -> "A cada " + intervaloMinutos + " min";
        };
    }

    private static String abreviar(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "Seg";
            case TUESDAY -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY -> "Qui";
            case FRIDAY -> "Sex";
            case SATURDAY -> "Sab";
            case SUNDAY -> "Dom";
        };
    }

    // ------------------------------------------------------ getters/setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isSensivel() {
        return sensivel;
    }

    public void setSensivel(boolean sensivel) {
        this.sensivel = sensivel;
    }

    public TipoRecorrencia getTipo() {
        return tipo;
    }

    public void setTipo(TipoRecorrencia tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }

    public Set<DayOfWeek> getDiasSemana() {
        return diasSemana;
    }

    public void setDiasSemana(Set<DayOfWeek> diasSemana) {
        this.diasSemana = diasSemana == null ? new LinkedHashSet<>() : diasSemana;
    }

    public Integer getDiaMes() {
        return diaMes;
    }

    public void setDiaMes(Integer diaMes) {
        this.diaMes = diaMes;
    }

    public Integer getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public void setIntervaloMinutos(Integer intervaloMinutos) {
        this.intervaloMinutos = intervaloMinutos;
    }

    public List<Integer> getAntecedencias() {
        return antecedencias;
    }

    public void setAntecedencias(List<Integer> antecedencias) {
        this.antecedencias = (antecedencias == null || antecedencias.isEmpty())
                ? new ArrayList<>(List.of(0))
                : antecedencias;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isSomAtivo() {
        return somAtivo;
    }

    public void setSomAtivo(boolean somAtivo) {
        this.somAtivo = somAtivo;
    }

    public LocalDateTime getDataExclusao() {
        return dataExclusao;
    }

    public void setDataExclusao(LocalDateTime dataExclusao) {
        this.dataExclusao = dataExclusao;
    }

    /** O lembrete foi excluído (logicamente)? */
    public boolean isExcluido() {
        return dataExclusao != null;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    @Override
    public String toString() {
        return "Lembrete#" + id;   // sem o título de propósito: pode ser conteúdo protegido
    }
}
