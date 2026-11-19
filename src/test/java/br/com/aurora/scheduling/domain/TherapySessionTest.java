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
    private static final UUID OWNER = UUID.randomUUID();


    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private TherapySession generated() {
        return TherapySession.generated(OWNER, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 28),
                LocalDateTime.of(2026, 9, 28, 9, 0), new BigDecimal("180.00"), NOW);
    }

    @Test
    @DisplayName("sessão gerada nasce agendada, pendente, com 50 minutos e terminando 50 minutos depois")
    void generatedDefaults() {
        TherapySession session = generated();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(session.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(session.getCancellationType()).isNull();
        assertThat(session.getDurationMinutes()).isEqualTo(50);
        assertThat(session.endsAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 9, 50));
        assertThat(session.getRescheduledFromId()).isNull();
        assertThat(session.isBillable()).isTrue();
    }

    @Test
    @DisplayName("mudar o valor atualiza só o valor")
    void changeAmount() {
        TherapySession session = generated();

        session.changeAmount(new BigDecimal("200.00"), NOW.plusSeconds(60));

        assertThat(session.getAmount()).isEqualByComparingTo("200.00");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
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
