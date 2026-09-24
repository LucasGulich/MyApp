package br.com.myapp.modules.lembretes;

/**
 * Como um lembrete se repete.
 *
 * Substitui as três opções do Lembrete.bat (único, diario, semanal) e
 * acrescenta as que faltavam no dia a dia.
 */
public enum TipoRecorrencia {

    UNICO("Uma vez só"),
    DIARIO("Todo dia"),
    DIAS_UTEIS("De segunda a sexta"),
    SEMANAL("Em dias da semana escolhidos"),
    MENSAL("Todo mês, em um dia fixo"),
    ANUAL("Uma vez por ano"),
    INTERVALO("A cada X minutos");

    private final String descricao;

    TipoRecorrencia(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /** Este tipo precisa que o usuário escolha dias da semana? */
    public boolean usaDiasDaSemana() {
        return this == SEMANAL;
    }

    /** Este tipo precisa de um dia do mês? */
    public boolean usaDiaDoMes() {
        return this == MENSAL;
    }

    /** Este tipo precisa de um intervalo em minutos? */
    public boolean usaIntervalo() {
        return this == INTERVALO;
    }

    /** Se repete indefinidamente (logo, aceita data de termino)? */
    public boolean eRepetido() {
        return this != UNICO;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
