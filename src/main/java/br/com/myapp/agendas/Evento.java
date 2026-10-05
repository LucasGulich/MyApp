package br.com.myapp.agendas;

import java.time.LocalDateTime;

/**
 * Uma ocorrência de um evento de agenda externa, já no horário deste
 * computador.
 *
 * Um evento que se repete gera um {@code Evento} por ocorrência: a "Daily das
 * 9h" de segunda e a de terça são dois.
 *
 * @param uid         identificador do evento no Google — o mesmo em todas
 *                    as ocorrências de uma série
 * @param titulo      nunca vazio: sem título vira "(sem título)"
 * @param descricao   texto livre, já sem o bloco de instruções do Meet; pode ser vazio
 * @param local       pode ser vazio
 * @param inicio      começo; meia-noite no evento de dia inteiro
 * @param fim         término; meia-noite do dia seguinte no de dia inteiro
 * @param diaInteiro  aniversário, feriado, férias — sem horário
 * @param linkReuniao Meet, Teams ou Zoom, se houver; senão null
 */
public record Evento(String uid, String titulo, String descricao, String local,
                     LocalDateTime inicio, LocalDateTime fim, boolean diaInteiro,
                     String linkReuniao) {

    /** Já terminou? */
    public boolean jaTerminou(LocalDateTime agora) {
        return !fim.isAfter(agora);
    }

    /** Começou e ainda não terminou? */
    public boolean emAndamento(LocalDateTime agora) {
        return !inicio.isAfter(agora) && fim.isAfter(agora);
    }
}
