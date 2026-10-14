package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.RecurrenceDetails;
import java.util.UUID;

/** Datas e horas como texto: "2026-09-28" e "09:00" (sem segundos, sem fuso). */
public record RecurrenceResponse(UUID id, String anchorDate, String startTime, String frequency, String endsOn,
        boolean active) {

    static RecurrenceResponse from(RecurrenceDetails d) {
        return new RecurrenceResponse(
                d.id(),
                d.anchorDate().toString(),
                d.startTime().toString().substring(0, 5),
                d.frequency(),
                d.endsOn() == null ? null : d.endsOn().toString(),
                d.endsOn() == null);
    }
}
