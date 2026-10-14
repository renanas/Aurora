package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.domain.TherapySession;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** O que a camada web pode saber de uma sessão. Enums viram texto para a web não depender do domínio. */
public record SessionDetails(
        UUID id,
        UUID patientId,
        String patientName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int durationMinutes,
        String status,
        String cancellationType,
        String paymentStatus,
        BigDecimal amount,
        boolean billable,
        UUID recurrenceId,
        UUID rescheduledFromId,
        List<UUID> overlapsWith) {

    static SessionDetails from(TherapySession s, String patientName, List<UUID> overlapsWith) {
        return new SessionDetails(
                s.getId(),
                s.getPatientId(),
                patientName,
                s.getStartsAt(),
                s.endsAt(),
                s.getDurationMinutes(),
                s.getStatus().name(),
                s.getCancellationType() == null ? null : s.getCancellationType().name(),
                s.getPaymentStatus().name(),
                s.getAmount(),
                s.isBillable(),
                s.getRecurrenceId(),
                s.getRescheduledFromId(),
                overlapsWith);
    }
}
