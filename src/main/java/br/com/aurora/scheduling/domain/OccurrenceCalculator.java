package br.com.aurora.scheduling.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Datas em que uma recorrência cai. Função pura: não olha relógio nem banco.
 * Semanal = +7 dias; quinzenal = +14 dias, sempre contados a partir da âncora (não por semana par/ímpar).
 */
public final class OccurrenceCalculator {

    private OccurrenceCalculator() {
    }

    /**
     * Ocorrências em [from, to], nunca antes da âncora e nunca depois de endsOn (se houver).
     * Ambos os limites são inclusivos.
     */
    public static List<LocalDate> occurrences(LocalDate anchor, Frequency frequency, LocalDate from, LocalDate to,
            LocalDate endsOn) {
        LocalDate last = (endsOn != null && endsOn.isBefore(to)) ? endsOn : to;
        LocalDate start = from.isAfter(anchor) ? from : anchor;
        if (start.isAfter(last)) {
            return List.of();
        }

        int step = frequency.stepDays();
        long daysFromAnchor = ChronoUnit.DAYS.between(anchor, start);
        long stepsToFirst = (daysFromAnchor + step - 1) / step;
        LocalDate current = anchor.plusDays(stepsToFirst * step);

        List<LocalDate> result = new ArrayList<>();
        while (!current.isAfter(last)) {
            result.add(current);
            current = current.plusDays(step);
        }
        return result;
    }
}
