package br.com.myapp.agendas;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uma agenda de fora conectada ao MyApp — hoje, a do Google, pelo endereço
 * secreto no formato iCal.
 *
 * O MyApp só lê: os eventos continuam sendo criados e alterados no Google.
 * Ver docs/AGENDAS.md.
 */
public class Agenda {

    private Long id;
    private String nome = "";
    private String cor = "#4A8CFF";

    /**
     * O endereço secreto, em claro. Só existe aqui enquanto alguém está
     * editando ou acabou de destrancar; no banco ele vai sempre cifrado.
     */
    private String link;

    private LocalDateTime sincronizadaEm;
    private String ultimoErro;
    private boolean ocultarRecusados = true;

    /** Minutos antes do início em que avisar. Vazia: esta agenda não avisa. */
    private List<Integer> antecedencias = new ArrayList<>(List.of(10));
    private boolean somAtivo;
    private boolean avisarDiaInteiro;

    /** A hora em que o evento de dia inteiro avisa, se avisar. */
    private LocalTime horaDiaInteiro = AvisosDeAgenda.HORA_PADRAO_DIA_INTEIRO;
    private boolean protegida;
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    /** Mensagem de erro pronta para a tela, ou null se estiver tudo certo. */
    public String validar() {
        if (nome == null || nome.isBlank()) {
            return "Dê um nome para a agenda, como \"Trabalho\".";
        }
        if (nome.length() > 60) {
            return "O nome pode ter no máximo 60 caracteres.";
        }
        if (link == null || link.isBlank()) {
            return "Cole o endereço secreto da agenda.";
        }
        if (!enderecoAceito(link)) {
            return "O endereço precisa começar com https://. No Google, ele fica em "
                    + "Configurações da agenda → Integrar agenda → Endereço secreto no formato iCal.";
        }
        return null;
    }

    /**
     * https, ou webcal (o mesmo endereço com outro nome).
     *
     * http puro só para a própria máquina: é o servidor de mentira dos testes
     * automáticos. Pela rede, o endereço secreto nunca viaja sem cifra.
     */
    static boolean enderecoAceito(String link) {
        String l = link == null ? "" : link.trim().toLowerCase();
        return l.startsWith("https://") || l.startsWith("webcal://")
                || l.startsWith("http://127.0.0.1:") || l.startsWith("http://localhost:");
    }

    /** Teve sincronização bem-sucedida alguma vez? */
    public boolean jaSincronizou() {
        return sincronizadaEm != null;
    }

    /** "5,10" — o formato da coluna. Vazio quando não há aviso. */
    public String antecedenciasCsv() {
        return antecedencias.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public void antecedenciasCsv(String csv) {
        antecedencias = csv == null || csv.isBlank()
                ? new ArrayList<>()
                : Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> s.matches("\\d+"))
                        .map(Integer::valueOf)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Avisa de algum jeito? */
    public boolean avisa() {
        return !antecedencias.isEmpty() || avisarDiaInteiro;
    }

    // ------------------------------------------------------------ acesso

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public LocalDateTime getSincronizadaEm() {
        return sincronizadaEm;
    }

    public void setSincronizadaEm(LocalDateTime sincronizadaEm) {
        this.sincronizadaEm = sincronizadaEm;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public void setUltimoErro(String ultimoErro) {
        this.ultimoErro = ultimoErro;
    }

    public boolean isOcultarRecusados() {
        return ocultarRecusados;
    }

    public void setOcultarRecusados(boolean ocultarRecusados) {
        this.ocultarRecusados = ocultarRecusados;
    }

    public List<Integer> getAntecedencias() {
        return antecedencias;
    }

    public void setAntecedencias(List<Integer> antecedencias) {
        this.antecedencias = antecedencias == null ? new ArrayList<>() : new ArrayList<>(antecedencias);
    }

    public boolean isSomAtivo() {
        return somAtivo;
    }

    public void setSomAtivo(boolean somAtivo) {
        this.somAtivo = somAtivo;
    }

    public boolean isAvisarDiaInteiro() {
        return avisarDiaInteiro;
    }

    public void setAvisarDiaInteiro(boolean avisarDiaInteiro) {
        this.avisarDiaInteiro = avisarDiaInteiro;
    }

    public LocalTime getHoraDiaInteiro() {
        return horaDiaInteiro;
    }

    public void setHoraDiaInteiro(LocalTime horaDiaInteiro) {
        this.horaDiaInteiro = horaDiaInteiro == null
                ? AvisosDeAgenda.HORA_PADRAO_DIA_INTEIRO
                : horaDiaInteiro.withSecond(0).withNano(0);
    }

    public boolean isProtegida() {
        return protegida;
    }

    public void setProtegida(boolean protegida) {
        this.protegida = protegida;
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
        // Nunca o link: toString acaba em log.
        return "Agenda{id=" + id + ", nome=" + nome + "}";
    }
}
