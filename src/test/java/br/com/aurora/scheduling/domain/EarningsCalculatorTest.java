package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aurora.scheduling.domain.EarningsCalculator.Band;
import br.com.aurora.scheduling.domain.EarningsCalculator.Entry;
import br.com.aurora.scheduling.domain.EarningsCalculator.GroupBy;
import br.com.aurora.scheduling.domain.EarningsCalculator.Period;
import br.com.aurora.scheduling.domain.EarningsCalculator.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarningsCalculatorTest {

    /** Segunda-feira. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDateTime NOW = MONDAY.plusDays(2).atTime(12, 0); // quarta, meio-dia

    private static Entry entry(LocalDate day, int hour, SessionStatus status, boolean billable, boolean paid,
            String amount) {
        LocalDateTime start = day.atTime(hour, 0);
        return new Entry(UUID.randomUUID(), UUID.randomUUID(), start, start.plusMinutes(50), status, billable, paid,
                new BigDecimal(amount));
    }

    private static Entry billable(LocalDate day, SessionStatus status, boolean paid, String amount) {
        return entry(day, 9, status, true, paid, amount);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("faixa de cada sessão: paga = recebido; com desfecho = a receber; agendada = previsto")
    void classifies() {
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.COMPLETED, true, "180")))
                .isEqualTo(Band.RECEIVED);
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.SCHEDULED, true, "180")))
                .isEqualTo(Band.RECEIVED); // pago adiantado
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.COMPLETED, false, "180")))
                .isEqualTo(Band.RECEIVABLE);
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.NO_SHOW, false, "180")))
                .isEqualTo(Band.RECEIVABLE);
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.CANCELLED, false, "180")))
                .isEqualTo(Band.RECEIVABLE); // cancelada em cima da hora (cobrável)
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.SCHEDULED, false, "180")))
                .isEqualTo(Band.EXPECTED);
        assertThat(EarningsCalculator.classify(billable(MONDAY, SessionStatus.CONFIRMED, false, "180")))
                .isEqualTo(Band.EXPECTED);
        assertThat(EarningsCalculator.classify(entry(MONDAY, 9, SessionStatus.CANCELLED, false, true, "180")))
                .isEqualTo(Band.NOT_BILLABLE);
    }

    @Test
    @DisplayName("as três faixas somam o total; não cobráveis ficam fora, e as pagas entre elas viram aviso")
    void totals() {
        List<Entry> entries = List.of(
                billable(MONDAY, SessionStatus.COMPLETED, true, "180.00"),
                billable(MONDAY.plusDays(1), SessionStatus.NO_SHOW, false, "200.00"),
                billable(MONDAY.plusDays(4), SessionStatus.SCHEDULED, false, "150.00"),
                entry(MONDAY.plusDays(2), 9, SessionStatus.CANCELLED, false, true, "90.00"), // cedo, mas paga
                entry(MONDAY.plusDays(2), 10, SessionStatus.RESCHEDULED, false, false, "180.00"));

        Result result = EarningsCalculator.calculate(entries, MONDAY, MONDAY.plusDays(6), GroupBy.DAY, NOW);

        assertThat(result.received()).isEqualByComparingTo("180");
        assertThat(result.receivable()).isEqualByComparingTo("200");
        assertThat(result.expected()).isEqualByComparingTo("150");
        assertThat(result.total()).isEqualByComparingTo("530");
        assertThat(result.paidNotBillableAmount()).isEqualByComparingTo("90");
        assertThat(result.paidNotBillableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("período sem sessões: tudo zero e uma barra por dia")
    void emptyPeriod() {
        Result result = EarningsCalculator.calculate(List.of(), MONDAY, MONDAY.plusDays(6), GroupBy.DAY, NOW);

        assertThat(result.total()).isEqualByComparingTo("0");
        assertThat(result.periods()).hasSize(7);
        assertThat(result.periods()).allSatisfy(period -> assertThat(period.received()).isEqualByComparingTo("0"));
        assertThat(result.receivableEntries()).isEmpty();
        assertThat(result.sessionsWithoutOutcome()).isZero();
    }

    @Test
    @DisplayName("por dia: cada sessão cai na barra do seu dia, em sua faixa")
    void groupsByDay() {
        List<Entry> entries = List.of(
                billable(MONDAY, SessionStatus.COMPLETED, true, "100"),
                entry(MONDAY, 15, SessionStatus.SCHEDULED, true, false, "50"),
                billable(MONDAY.plusDays(2), SessionStatus.COMPLETED, false, "70"));

        List<Period> periods = EarningsCalculator.calculate(entries, MONDAY, MONDAY.plusDays(2), GroupBy.DAY, NOW)
                .periods();

        assertThat(periods).extracting(Period::start)
                .containsExactly(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2));
        assertThat(periods.get(0).received()).isEqualByComparingTo("100");
        assertThat(periods.get(0).expected()).isEqualByComparingTo("50");
        assertThat(periods.get(1).received()).isEqualByComparingTo("0");
        assertThat(periods.get(2).receivable()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("por semana: segunda a domingo, com a primeira e a última semana cortadas pelo mês")
    void groupsByWeekClippedToTheMonth() {
        LocalDate from = LocalDate.of(2026, 11, 1); // domingo
        LocalDate to = LocalDate.of(2026, 11, 30);  // segunda
        List<Entry> entries = List.of(
                billable(from, SessionStatus.COMPLETED, true, "100"),
                billable(LocalDate.of(2026, 11, 2), SessionStatus.COMPLETED, true, "40"),
                billable(LocalDate.of(2026, 11, 8), SessionStatus.COMPLETED, true, "60"),
                billable(to, SessionStatus.SCHEDULED, false, "80"));

        List<Period> periods = EarningsCalculator.calculate(entries, from, to, GroupBy.WEEK, NOW).periods();

        assertThat(periods).extracting(Period::start).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30));
        assertThat(periods).extracting(Period::end).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 30));
        assertThat(periods.get(0).received()).isEqualByComparingTo("100");
        assertThat(periods.get(1).received()).isEqualByComparingTo("100");
        assertThat(periods.get(5).expected()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("sessão já terminada e ainda agendada/confirmada continua no previsto, com o alerta")
    void countsPastSessionsWithoutOutcome() {
        List<Entry> entries = List.of(
                billable(MONDAY, SessionStatus.SCHEDULED, false, "100"),            // segunda: já passou
                billable(MONDAY.plusDays(1), SessionStatus.CONFIRMED, false, "100"), // terça: já passou
                entry(NOW.toLocalDate(), 12, SessionStatus.SCHEDULED, true, false, "100"), // em andamento
                billable(MONDAY.plusDays(4), SessionStatus.SCHEDULED, false, "100"), // sexta: futuro
                billable(MONDAY, SessionStatus.SCHEDULED, true, "100"));            // passou, mas paga

        Result result = EarningsCalculator.calculate(entries, MONDAY, MONDAY.plusDays(6), GroupBy.DAY, NOW);

        assertThat(result.expected()).isEqualByComparingTo("400");
        assertThat(result.sessionsWithoutOutcome()).isEqualTo(2);
    }

    @Test
    @DisplayName("lista a receber em ordem de data, só com as sessões a receber")
    void receivableListIsSorted() {
        Entry late = billable(MONDAY.plusDays(3), SessionStatus.NO_SHOW, false, "100");
        Entry early = billable(MONDAY, SessionStatus.COMPLETED, false, "100");
        List<Entry> entries = List.of(late, billable(MONDAY, SessionStatus.COMPLETED, true, "100"), early);

        Result result = EarningsCalculator.calculate(entries, MONDAY, MONDAY.plusDays(6), GroupBy.DAY, NOW);

        assertThat(result.receivableEntries()).containsExactly(early, late);
    }

    @Test
    @DisplayName("sessões fora do período são ignoradas")
    void ignoresOutsideTheRange() {
        List<Entry> entries = List.of(
                billable(MONDAY.minusDays(1), SessionStatus.COMPLETED, true, "100"),
                billable(MONDAY.plusDays(7), SessionStatus.COMPLETED, true, "100"));

        Result result = EarningsCalculator.calculate(entries, MONDAY, MONDAY.plusDays(6), GroupBy.WEEK, NOW);

        assertThat(result.total()).isEqualByComparingTo("0");
        assertThat(result.periods()).hasSize(1);
        assertThat(money("0.00")).isEqualByComparingTo(result.received());
    }
}
