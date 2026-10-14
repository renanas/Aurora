package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.SessionDetails;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Horários como texto "2026-09-28T09:00": sem segundos e sem fuso, como combinado na API. */
public record SessionResponse(
        UUID id,
        UUID patientId,
        String patientName,
        String startsAt,
        String endsAt,
        int durationMinutes,
        String status,
        String cancellationType,
        String paymentStatus,
        BigDecimal amount,
        boolean billable,
        UUID recurrenceId,
        UUID rescheduledFromId,
        List<UUID> overlapsWith) {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    static SessionResponse from(SessionDetails d) {
        return new SessionResponse(
                d.id(),
                d.patientId(),
                d.patientName(),
                LOCAL_DATE_TIME.format(d.startsAt()),
                LOCAL_DATE_TIME.format(d.endsAt()),
                d.durationMinutes(),
                d.status(),
                d.cancellationType(),
                d.paymentStatus(),
                d.amount(),
                d.billable(),
                d.recurrenceId(),
                d.rescheduledFromId(),
                d.overlapsWith());
    }
}
