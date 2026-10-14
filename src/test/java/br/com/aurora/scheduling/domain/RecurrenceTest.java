package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 28);

    private Recurrence weeklyAtNine() {
        return new Recurrence(UUID.randomUUID(), ANCHOR, LocalTime.of(9, 0), Frequency.WEEKLY,
                Instant.parse("2026-09-20T12:00:00Z"));
    }

    @Test
    @DisplayName("nasce ativa; encerrar define o último dia de vigência")
    void startsActiveAndEnds() {
        Recurrence r = weeklyAtNine();
        assertThat(r.isActive()).isTrue();

        r.endOn(LocalDate.of(2026, 10, 10));

        assertThat(r.isActive()).isFalse();
        assertThat(r.getEndsOn()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("o último dia pode ser a própria âncora, mas nunca antes dela")
    void endCannotBeBeforeAnchor() {
        Recurrence r = weeklyAtNine();

        r.endOn(ANCHOR);
        assertThat(r.getEndsOn()).isEqualTo(ANCHOR);

        assertThatThrownBy(() -> r.endOn(ANCHOR.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hasSameSchedule compara âncora, horário e frequência")
    void sameSchedule() {
        Recurrence r = weeklyAtNine();

        assertThat(r.hasSameSchedule(ANCHOR, LocalTime.of(9, 0), Frequency.WEEKLY)).isTrue();
        assertThat(r.hasSameSchedule(ANCHOR.plusDays(7), LocalTime.of(9, 0), Frequency.WEEKLY)).isFalse();
        assertThat(r.hasSameSchedule(ANCHOR, LocalTime.of(10, 0), Frequency.WEEKLY)).isFalse();
        assertThat(r.hasSameSchedule(ANCHOR, LocalTime.of(9, 0), Frequency.BIWEEKLY)).isFalse();
    }
}
