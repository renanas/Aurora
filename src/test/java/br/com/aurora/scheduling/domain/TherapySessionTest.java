package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TherapySessionTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private TherapySession generated() {
        return TherapySession.generated(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 28),
                LocalDateTime.of(2026, 9, 28, 9, 0), new BigDecimal("180.00"), NOW);
    }

    @Test
    @DisplayName("sessão gerada nasce agendada, pendente, com 50 minutos e terminando 50 minutos depois")
    void generatedDefaults() {
        TherapySession s = generated();

        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(s.getCancellationType()).isNull();
        assertThat(s.getDurationMinutes()).isEqualTo(50);
        assertThat(s.endsAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 9, 50));
        assertThat(s.getRescheduledFromId()).isNull();
        assertThat(s.isBillable()).isTrue();
    }

    @Test
    @DisplayName("mudar o valor atualiza só o valor")
    void changeAmount() {
        TherapySession s = generated();

        s.changeAmount(new BigDecimal("200.00"), NOW.plusSeconds(60));

        assertThat(s.getAmount()).isEqualByComparingTo("200.00");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("só AGENDADA, CONFIRMADA, REALIZADA e FALTOU ocupam o horário")
    void statusesThatOccupyTheSlot() {
        assertThat(SessionStatus.SCHEDULED.occupiesSlot()).isTrue();
        assertThat(SessionStatus.CONFIRMED.occupiesSlot()).isTrue();
        assertThat(SessionStatus.COMPLETED.occupiesSlot()).isTrue();
        assertThat(SessionStatus.NO_SHOW.occupiesSlot()).isTrue();
        assertThat(SessionStatus.CANCELLED.occupiesSlot()).isFalse();
        assertThat(SessionStatus.RESCHEDULED.occupiesSlot()).isFalse();
    }
}
