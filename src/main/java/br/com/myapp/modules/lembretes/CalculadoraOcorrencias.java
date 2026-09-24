package br.com.myapp.modules.lembretes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Descobre quando um lembrete acontece.
 *
 * Esta classe e o coração do agendamento e não depende de banco, de tela nem
 * de relógio do sistema: recebe o lembrete e a janela de tempo e devolve as
 * datas. Isso a torna fácil de testar, que e exatamente o que se espera de
 * uma regra de negócio.
 */
public final class CalculadoraOcorrencias {

    /**
     * Teto de segurança. Um lembrete "a cada 1 minuto" consultado em uma
     * janela de um ano geraria centenas de milhares de datas; o limite evita
     * que um cadastro distraido trave o aplicativo.
     */
    private static final int MAXIMO_DE_OCORRENCIAS = 5_000;

    private CalculadoraOcorrencias() {
    }

    /**
     * Lista as ocorrências do lembrete dentro da janela informada.
     *
     * @param de  inicio da janela (inclusivo)
     * @param até fim da janela (inclusivo)
     */
    public static List<LocalDateTime> entre(Lembrete lembrete, LocalDateTime de, LocalDateTime ate) {
        List<LocalDateTime> resultado = new ArrayList<>();
        if (lembrete == null || lembrete.getInicio() == null || de.isAfter(ate)) {
            return resultado;
        }

        LocalDateTime inicio = lembrete.getInicio();
        LocalDateTime limite = menorEntre(ate, lembrete.getFim());
        if (limite == null || limite.isBefore(de)) {
            return resultado;
        }

        switch (lembrete.getTipo()) {
            case UNICO -> {
                if (!inicio.isBefore(de) && !inicio.isAfter(limite)) {
                    resultado.add(inicio);
                }
            }
            case INTERVALO -> preencherPorIntervalo(lembrete, de, limite, resultado);
            default -> preencherPorDia(lembrete, de, limite, resultado);
        }
        return resultado;
    }

    /**
     * Próxima ocorrência a partir de um instante. Devolve vazio quando o
     * lembrete já terminou.
     */
    public static Optional<LocalDateTime> proxima(Lembrete lembrete, LocalDateTime apartirDe) {
        if (lembrete == null || lembrete.getInicio() == null) {
            return Optional.empty();
        }
        // Procura em janelas cada vez maiores: resolve o caso comum em uma
        // passada e ainda encontra um lembrete anual sem varrer o ano inteiro.
        int[] janelasEmDias = {2, 14, 70, 400};
        for (int dias : janelasEmDias) {
            List<LocalDateTime> lista = entre(lembrete, apartirDe, apartirDe.plusDays(dias));
            if (!lista.isEmpty()) {
                return Optional.of(lista.get(0));
            }
            if (lembrete.getFim() != null && lembrete.getFim().isBefore(apartirDe.plusDays(dias))) {
                break;
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------- internos

    /** Trata os tipos que acontecem uma vez por dia elegivel, sempre no mesmo horário. */
    private static void preencherPorDia(Lembrete lembrete, LocalDateTime de, LocalDateTime limite,
                                        List<LocalDateTime> resultado) {
        LocalDateTime inicio = lembrete.getInicio();

        // Não existe ocorrência antes do inicio do lembrete.
        LocalDate dia = de.isBefore(inicio) ? inicio.toLocalDate() : de.toLocalDate();
        LocalDate ultimoDia = limite.toLocalDate();

        while (!dia.isAfter(ultimoDia) && resultado.size() < MAXIMO_DE_OCORRENCIAS) {
            LocalDateTime candidato = ajustarDia(lembrete, dia);
            if (candidato != null
                    && !candidato.isBefore(de)
                    && !candidato.isBefore(inicio)
                    && !candidato.isAfter(limite)) {
                resultado.add(candidato);
            }
            dia = dia.plusDays(1);
        }
    }

    /**
     * Devolve a data e hora da ocorrência naquele dia, ou nulo se o dia não
     * for elegivel para este tipo de recorrência.
     */
    private static LocalDateTime ajustarDia(Lembrete lembrete, LocalDate dia) {
        LocalDateTime inicio = lembrete.getInicio();

        return switch (lembrete.getTipo()) {
            case DIARIO -> dia.atTime(inicio.toLocalTime());

            case DIAS_UTEIS -> eDiaUtil(dia) ? dia.atTime(inicio.toLocalTime()) : null;

            case SEMANAL -> lembrete.getDiasSemana().contains(dia.getDayOfWeek())
                    ? dia.atTime(inicio.toLocalTime())
                    : null;

            case MENSAL -> {
                int desejado = lembrete.getDiaMes() == null
                        ? inicio.getDayOfMonth()
                        : lembrete.getDiaMes();
                // Dia 31 em fevereiro cai no último dia do mês, em vez de sumir.
                int diaValido = Math.min(desejado, dia.lengthOfMonth());
                yield dia.getDayOfMonth() == diaValido ? dia.atTime(inicio.toLocalTime()) : null;
            }

            case ANUAL -> {
                int diaDesejado = Math.min(inicio.getDayOfMonth(), dia.lengthOfMonth());
                yield (dia.getMonthValue() == inicio.getMonthValue() && dia.getDayOfMonth() == diaDesejado)
                        ? dia.atTime(inicio.toLocalTime())
                        : null;
            }

            case UNICO, INTERVALO -> null; // tratados fora deste caminho
        };
    }

    /** Trata a repetição "a cada X minutos". */
    private static void preencherPorIntervalo(Lembrete lembrete, LocalDateTime de, LocalDateTime limite,
                                              List<LocalDateTime> resultado) {
        int passo = lembrete.getIntervaloMinutos() == null ? 0 : lembrete.getIntervaloMinutos();
        if (passo < 1) {
            return;
        }
        LocalDateTime inicio = lembrete.getInicio();

        LocalDateTime atual;
        if (de.isAfter(inicio)) {
            // Pula direto para a primeira ocorrência dentro da janela, sem
            // iterar desde o inicio do lembrete.
            long minutosPassados = ChronoUnit.MINUTES.between(inicio, de);
            long saltos = (minutosPassados + passo - 1) / passo;
            atual = inicio.plusMinutes(saltos * passo);
        } else {
            atual = inicio;
        }

        while (!atual.isAfter(limite) && resultado.size() < MAXIMO_DE_OCORRENCIAS) {
            if (!atual.isBefore(de)) {
                resultado.add(atual);
            }
            atual = atual.plusMinutes(passo);
        }
    }

    private static boolean eDiaUtil(LocalDate dia) {
        DayOfWeek d = dia.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    /** Menor entre o fim da janela e o termino do lembrete (que pode ser nulo). */
    private static LocalDateTime menorEntre(LocalDateTime fimDaJanela, LocalDateTime fimDoLembrete) {
        if (fimDoLembrete == null) {
            return fimDaJanela;
        }
        return fimDoLembrete.isBefore(fimDaJanela) ? fimDoLembrete : fimDaJanela;
    }
}
