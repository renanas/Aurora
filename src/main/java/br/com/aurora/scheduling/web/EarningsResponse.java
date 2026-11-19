package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.EarningsDetails;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Datas como texto ("2026-11-16", "2026-11-16T09:00"), sem segundos e sem fuso, como o resto da API. */
public record EarningsResponse(
        String from,
        String to,
        String groupBy,
        Totals totals,
        PaidNotBillable paidNotBillable,
        int sessionsWithoutOutcome,
        boolean forecastIncomplete,
        List<Period> periods,
        List<ReceivableSession> receivableSessions) {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public record Totals(BigDecimal received, BigDecimal receivable, BigDecimal expected, BigDecimal total) {
    }

    public record PaidNotBillable(BigDecimal amount, int count) {
    }

    public record Period(String start, String end, BigDecimal received, BigDecimal receivable, BigDecimal expected) {
    }

    public record ReceivableSession(UUID sessionId, UUID patientId, String patientName, String startsAt,
            String status, BigDecimal amount) {
    }

    static EarningsResponse from(EarningsDetails details) {
        return new EarningsResponse(
                details.from().toString(),
                details.to().toString(),
                details.groupBy(),
                new Totals(details.received(), details.receivable(), details.expected(), details.total()),
                new PaidNotBillable(details.paidNotBillableAmount(), details.paidNotBillableCount()),
                details.sessionsWithoutOutcome(),
                details.forecastIncomplete(),
                details.periods().stream()
                        .map(period -> new Period(period.start().toString(), period.end().toString(), period.received(),
                                period.receivable(), period.expected()))
                        .toList(),
                details.receivableSessions().stream()
                        .map(receivableSession -> new ReceivableSession(receivableSession.sessionId(), receivableSession.patientId(), receivableSession.patientName(),
                                LOCAL_DATE_TIME.format(receivableSession.startsAt()), receivableSession.status(), receivableSession.amount()))
                        .toList());
    }
}
