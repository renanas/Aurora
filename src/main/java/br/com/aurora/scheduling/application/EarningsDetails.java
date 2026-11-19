package br.com.aurora.scheduling.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Ganhos de um período, para a camada web. Enums viram texto (a web não depende do domínio). */
public record EarningsDetails(
        LocalDate from,
        LocalDate to,
        String groupBy,
        BigDecimal received,
        BigDecimal receivable,
        BigDecimal expected,
        BigDecimal total,
        BigDecimal paidNotBillableAmount,
        int paidNotBillableCount,
        int sessionsWithoutOutcome,
        /** O período passa do horizonte de geração das sessões recorrentes: o previsto está incompleto. */
        boolean forecastIncomplete,
        List<Period> periods,
        List<ReceivableSession> receivableSessions) {

    public record Period(LocalDate start, LocalDate end, BigDecimal received, BigDecimal receivable,
            BigDecimal expected) {
    }

    public record ReceivableSession(UUID sessionId, UUID patientId, String patientName, LocalDateTime startsAt,
            String status, BigDecimal amount) {
    }
}
