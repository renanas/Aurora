package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Agenda (entrega D): sessão avulsa, corrigir horário, reagendar, desfazer e reabrir, sem banco. */
class TherapySessionPlanningTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final LocalDateTime ELEVEN = LocalDateTime.of(2026, 10, 20, 11, 0);

    /** O id real só nasce ao gravar no banco; aqui é preciso um para ligar reagendamentos. */
    private static TherapySession newSession() {
        TherapySession s = TherapySession.generated(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 20),
                NINE, new BigDecimal("180.00"), NOW);
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        return s;
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    @DisplayName("sessão avulsa: sem recorrência, agendada, pendente e com 50 minutos")
    void standaloneSession() {
        TherapySession s = TherapySession.standalone(UUID.randomUUID(), NINE, new BigDecimal("150.00"), NOW);

        assertThat(s.getRecurrenceId()).isNull();
        assertThat(s.getOccurrenceDate()).isNull();
        assertThat(s.getRescheduledFromId()).isNull();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(s.getDurationMinutes()).isEqualTo(50);
    }

    @Test
    @DisplayName("corrigir horário: muda só o início, mantém a data de origem; repetir não muda nada")
    void changeStart() {
        TherapySession s = newSession();

        s.changeStart(ELEVEN, NOW);
        s.changeStart(ELEVEN, NOW);

        assertThat(s.getStartsAt()).isEqualTo(ELEVEN);
        assertThat(s.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("corrigir horário só vale para sessão AGENDADA: SESSION_NOT_SCHEDULED")
    void changeStartOnlyWhenScheduled() {
        TherapySession s = newSession();
        s.confirm(NOW);

        assertRule(() -> s.changeStart(ELEVEN, NOW), "SESSION_NOT_SCHEDULED");
    }

    @Test
    @DisplayName("reagendar: a original vira REAGENDADA e a nova leva valor e pagamento")
    void rescheduleMovesThePayment() {
        TherapySession original = newSession();
        Instant paidAt = Instant.parse("2026-10-01T10:00:00Z");
        original.markPaid(paidAt);

        TherapySession next = original.rescheduleTo(ELEVEN, NOW);

        assertThat(original.getStatus()).isEqualTo(SessionStatus.RESCHEDULED);
        assertThat(original.isBillable()).isFalse();
        assertThat(original.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(original.getPaidAt()).isNull();

        assertThat(next.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(next.getRescheduledFromId()).isEqualTo(original.getId());
        assertThat(next.getRecurrenceId()).isNull();
        assertThat(next.getOccurrenceDate()).isNull();
        assertThat(next.getAmount()).isEqualByComparingTo("180.00");
        assertThat(next.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(next.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("reagendar: só sessão aberta e só para outro horário")
    void rescheduleRules() {
        TherapySession sameTime = newSession();
        assertRule(() -> sameTime.rescheduleTo(NINE, NOW), "RESCHEDULE_SAME_TIME");

        TherapySession noShow = newSession();
        noShow.markNoShow(NOW);
        assertRule(() -> noShow.rescheduleTo(ELEVEN, NOW), "INVALID_STATUS_TRANSITION");

        TherapySession alreadyMoved = newSession();
        alreadyMoved.rescheduleTo(ELEVEN, NOW);
        assertRule(() -> alreadyMoved.rescheduleTo(ELEVEN.plusHours(1), NOW), "INVALID_STATUS_TRANSITION");
    }

    @Test
    @DisplayName("desfazer reagendamento: a original volta com o pagamento e a nova é cancelada sem cobrança")
    void undoReschedule() {
        TherapySession original = newSession();
        Instant paidAt = Instant.parse("2026-10-01T10:00:00Z");
        original.markPaid(paidAt);
        TherapySession next = original.rescheduleTo(ELEVEN, NOW);
        ReflectionTestUtils.setField(next, "id", UUID.randomUUID());

        next.undoReschedule(original, NOW);

        assertThat(original.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(original.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(original.getPaidAt()).isEqualTo(paidAt);
        assertThat(next.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(next.getCancellationType()).isEqualTo(CancellationType.EARLY);
        assertThat(next.isBillable()).isFalse();
        assertThat(next.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(next.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("desfazer reagendamento: não vale se a nova já foi confirmada ou se não veio de reagendamento")
    void undoRescheduleRules() {
        TherapySession original = newSession();
        TherapySession confirmedNext = original.rescheduleTo(ELEVEN, NOW);
        ReflectionTestUtils.setField(confirmedNext, "id", UUID.randomUUID());
        confirmedNext.confirm(NOW);
        assertRule(() -> confirmedNext.undoReschedule(original, NOW), "UNDO_RESCHEDULE_NOT_ALLOWED");

        TherapySession plain = newSession();
        TherapySession other = newSession();
        assertRule(() -> plain.undoReschedule(other, NOW), "UNDO_RESCHEDULE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("reabrir: realizada, falta e cancelada voltam a AGENDADA; cancelada perde o tipo")
    void reopen() {
        TherapySession completed = newSession();
        completed.complete(NOW);
        completed.reopen(NOW);
        TherapySession noShow = newSession();
        noShow.markNoShow(NOW);
        noShow.reopen(NOW);
        TherapySession cancelled = newSession();
        cancelled.cancel(CancellationType.LATE, NOW);
        cancelled.reopen(NOW);

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(noShow.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(cancelled.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(cancelled.getCancellationType()).isNull();
        assertThat(cancelled.isBillable()).isTrue();
    }

    @Test
    @DisplayName("reabrir: repetir é inofensivo; confirmada e reagendada não reabrem (REOPEN_NOT_ALLOWED)")
    void reopenRules() {
        TherapySession scheduled = newSession();
        scheduled.reopen(NOW);
        assertThat(scheduled.getStatus()).isEqualTo(SessionStatus.SCHEDULED);

        TherapySession confirmed = newSession();
        confirmed.confirm(NOW);
        assertRule(() -> confirmed.reopen(NOW), "REOPEN_NOT_ALLOWED");

        TherapySession moved = newSession();
        moved.rescheduleTo(ELEVEN, NOW);
        assertRule(() -> moved.reopen(NOW), "REOPEN_NOT_ALLOWED");
    }

    @Test
    @DisplayName("reabrir não mexe no pagamento")
    void reopenKeepsThePayment() {
        TherapySession s = newSession();
        s.markPaid(NOW);
        s.cancel(CancellationType.EARLY, NOW);

        s.reopen(NOW);

        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(s.isBillable()).isTrue();
    }
}
