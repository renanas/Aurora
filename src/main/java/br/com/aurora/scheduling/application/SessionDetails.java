package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.shared.time.SystemConstants;
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
        LocalDateTime paidAt,
        BigDecimal amount,
        boolean billable,
        UUID recurrenceId,
        UUID rescheduledFromId,
        List<UUID> overlapsWith) {

    static SessionDetails from(TherapySession session, String patientName, List<UUID> overlapsWith) {
        return new SessionDetails(
                session.getId(),
                session.getPatientId(),
                patientName,
                session.getStartsAt(),
                session.endsAt(),
                session.getDurationMinutes(),
                session.getStatus().name(),
                session.getCancellationType() == null ? null : session.getCancellationType().name(),
                session.getPaymentStatus().name(),
                session.getPaidAt() == null ? null : LocalDateTime.ofInstant(session.getPaidAt(), SystemConstants.ZONE),
                session.getAmount(),
                session.isBillable(),
                session.getRecurrenceId(),
                session.getRescheduledFromId(),
                overlapsWith);
    }
}
