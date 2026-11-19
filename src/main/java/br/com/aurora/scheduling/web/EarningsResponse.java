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

    static EarningsResponse from(EarningsDetails d) {
        return new EarningsResponse(
                d.from().toString(),
                d.to().toString(),
                d.groupBy(),
                new Totals(d.received(), d.receivable(), d.expected(), d.total()),
                new PaidNotBillable(d.paidNotBillableAmount(), d.paidNotBillableCount()),
                d.sessionsWithoutOutcome(),
                d.forecastIncomplete(),
                d.periods().stream()
                        .map(p -> new Period(p.start().toString(), p.end().toString(), p.received(),
                                p.receivable(), p.expected()))
                        .toList(),
                d.receivableSessions().stream()
                        .map(s -> new ReceivableSession(s.sessionId(), s.patientId(), s.patientName(),
                                LOCAL_DATE_TIME.format(s.startsAt()), s.status(), s.amount()))
                        .toList());
    }
}
