package br.com.aurora.scheduling.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ganhos de um período, pela data da sessão (starts_at). Três faixas que somam o total:
 *  - Recebido: sessão cobrável e paga;
 *  - A receber: cobrável, pendente e já com desfecho (realizada, falta ou cancelada em cima da hora);
 *  - Previsto: cobrável, pendente e ainda agendada ou confirmada.
 * Sessão não cobrável (cancelada com antecedência, reagendada) fica fora das três; se mesmo assim estiver
 * paga, entra só no aviso "pagas em sessões não cobráveis", para o total bater com o extrato.
 * A regra de "cobrável" é a da própria sessão ({@link TherapySession#isBillable()}), não é repetida aqui.
 */
public final class EarningsCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public enum Band { RECEIVED, RECEIVABLE, EXPECTED, NOT_BILLABLE }

    public enum GroupBy { DAY, WEEK }

    /** O que o cálculo precisa saber de cada sessão. */
    public record Entry(UUID sessionId, UUID patientId, LocalDateTime startsAt, LocalDateTime endsAt,
            SessionStatus status, boolean billable, boolean paid, BigDecimal amount) {

        public Entry {
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(amount, "amount");
        }

        public static Entry of(TherapySession session) {
            return new Entry(session.getId(), session.getPatientId(), session.getStartsAt(), session.endsAt(), session.getStatus(),
                    session.isBillable(), session.getPaymentStatus() == PaymentStatus.PAID, session.getAmount());
        }
    }

    /** Uma barra do gráfico: um dia, ou uma semana (segunda a domingo) cortada pelos limites do período. */
    public record Period(LocalDate start, LocalDate end, BigDecimal received, BigDecimal receivable,
            BigDecimal expected) {
    }

    public record Result(
            BigDecimal received,
            BigDecimal receivable,
            BigDecimal expected,
            BigDecimal paidNotBillableAmount,
            int paidNotBillableCount,
            int sessionsWithoutOutcome,
            List<Period> periods,
            List<Entry> receivableEntries) {

        public BigDecimal total() {
            return received.add(receivable).add(expected);
        }
    }

    private EarningsCalculator() {
    }

    public static Band classify(Entry entry) {
        if (!entry.billable()) {
            return Band.NOT_BILLABLE;
        }
        if (entry.paid()) {
            return Band.RECEIVED;
        }
        return switch (entry.status()) {
            case SCHEDULED, CONFIRMED -> Band.EXPECTED;
            default -> Band.RECEIVABLE;
        };
    }

    /**
     * Sessões fora de [from, to] são ignoradas. {@code now} serve para contar as sessões que já terminaram e
     * continuam agendadas/confirmadas ("sem desfecho"): elas seguem no Previsto, com o alerta.
     */
    public static Result calculate(List<Entry> entries, LocalDate from, LocalDate to, GroupBy groupBy,
            LocalDateTime now) {
        Objects.requireNonNull(groupBy, "groupBy");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to antes de from");
        }

        List<LocalDate[]> bounds = periodBounds(from, to, groupBy);
        Map<LocalDate, Integer> indexByDay = new HashMap<>();
        for (int i = 0; i < bounds.size(); i++) {
            for (LocalDate day = bounds.get(i)[0]; !day.isAfter(bounds.get(i)[1]); day = day.plusDays(1)) {
                indexByDay.put(day, i);
            }
        }
        BigDecimal[][] sums = new BigDecimal[bounds.size()][3];
        for (BigDecimal[] row : sums) {
            Arrays.fill(row, ZERO);
        }

        BigDecimal received = ZERO;
        BigDecimal receivable = ZERO;
        BigDecimal expected = ZERO;
        BigDecimal paidNotBillable = ZERO;
        int paidNotBillableCount = 0;
        int withoutOutcome = 0;
        List<Entry> receivableEntries = new ArrayList<>();

        for (Entry entry : entries) {
            Integer index = indexByDay.get(entry.startsAt().toLocalDate());
            if (index == null) {
                continue;
            }
            switch (classify(entry)) {
                case RECEIVED -> {
                    received = received.add(entry.amount());
                    sums[index][0] = sums[index][0].add(entry.amount());
                }
                case RECEIVABLE -> {
                    receivable = receivable.add(entry.amount());
                    sums[index][1] = sums[index][1].add(entry.amount());
                    receivableEntries.add(entry);
                }
                case EXPECTED -> {
                    expected = expected.add(entry.amount());
                    sums[index][2] = sums[index][2].add(entry.amount());
                    if (!entry.endsAt().isAfter(now)) {
                        withoutOutcome++;
                    }
                }
                case NOT_BILLABLE -> {
                    if (entry.paid()) {
                        paidNotBillable = paidNotBillable.add(entry.amount());
                        paidNotBillableCount++;
                    }
                }
            }
        }

        List<Period> periods = new ArrayList<>(bounds.size());
        for (int i = 0; i < bounds.size(); i++) {
            periods.add(new Period(bounds.get(i)[0], bounds.get(i)[1], sums[i][0], sums[i][1], sums[i][2]));
        }
        receivableEntries.sort(Comparator.comparing(Entry::startsAt));
        return new Result(received, receivable, expected, paidNotBillable, paidNotBillableCount, withoutOutcome,
                List.copyOf(periods), List.copyOf(receivableEntries));
    }

    /** Por dia: um período por data. Por semana: segunda a domingo, a primeira e a última cortadas pelo período. */
    private static List<LocalDate[]> periodBounds(LocalDate from, LocalDate to, GroupBy groupBy) {
        List<LocalDate[]> bounds = new ArrayList<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = groupBy == GroupBy.DAY
                    ? start
                    : start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (end.isAfter(to)) {
                end = to;
            }
            bounds.add(new LocalDate[] {start, end});
            start = end.plusDays(1);
        }
        return bounds;
    }
}
