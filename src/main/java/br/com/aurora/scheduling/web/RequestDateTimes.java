package br.com.aurora.scheduling.web;

import br.com.aurora.shared.error.BusinessRuleException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Leitura dos horários "2026-09-28T09:00" recebidos pela API. */
final class RequestDateTimes {

    static final String PATTERN = "^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d$";

    private RequestDateTimes() {
    }

    /** O padrão aceita "2026-02-30T09:00"; o calendário não: vira 422 em vez de erro 500. */
    static LocalDateTime parse(String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("INVALID_DATE_TIME", "Essa data não existe no calendário.");
        }
    }
}
