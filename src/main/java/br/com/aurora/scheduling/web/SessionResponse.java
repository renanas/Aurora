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
        String paidAt,
        BigDecimal amount,
        boolean billable,
        UUID recurrenceId,
        UUID rescheduledFromId,
        List<UUID> overlapsWith) {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    static SessionResponse from(SessionDetails details) {
        return new SessionResponse(
                details.id(),
                details.patientId(),
                details.patientName(),
                LOCAL_DATE_TIME.format(details.startsAt()),
                LOCAL_DATE_TIME.format(details.endsAt()),
                details.durationMinutes(),
                details.status(),
                details.cancellationType(),
                details.paymentStatus(),
                details.paidAt() == null ? null : LOCAL_DATE_TIME.format(details.paidAt()),
                details.amount(),
                details.billable(),
                details.recurrenceId(),
                details.rescheduledFromId(),
                details.overlapsWith());
    }
}
