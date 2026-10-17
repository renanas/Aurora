package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ações da sessão (entrega C): transições de situação e pagamento, sem banco. */
class TherapySessionActionsTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private static TherapySession newSession() {
        return TherapySession.generated(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 29),
                LocalDateTime.of(2026, 9, 29, 9, 0), new BigDecimal("180.00"), NOW);
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    @DisplayName("confirmar: AGENDADA vira CONFIRMADA, e repetir não muda nada")
    void confirmIsIdempotent() {
        TherapySession s = newSession();

        s.confirm(NOW);
        s.confirm(NOW);

        assertThat(s.getStatus()).isEqualTo(SessionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("realizada: vale a partir de AGENDADA e de CONFIRMADA")
    void completeFromOpenStatuses() {
        TherapySession fromScheduled = newSession();
        fromScheduled.complete(NOW);
        TherapySession fromConfirmed = newSession();
        fromConfirmed.confirm(NOW);
        fromConfirmed.complete(NOW);

        assertThat(fromScheduled.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(fromConfirmed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("falta: continua cobrável")
    void noShowStaysBillable() {
        TherapySession s = newSession();

        s.markNoShow(NOW);

        assertThat(s.getStatus()).isEqualTo(SessionStatus.NO_SHOW);
        assertThat(s.isBillable()).isTrue();
    }

    @Test
    @DisplayName("ação em sessão já resolvida (realizada, falta, cancelada): INVALID_STATUS_TRANSITION")
    void resolvedSessionsRejectOtherActions() {
        TherapySession completed = newSession();
        completed.complete(NOW);
        assertRule(() -> completed.confirm(NOW), "INVALID_STATUS_TRANSITION");
        assertRule(() -> completed.markNoShow(NOW), "INVALID_STATUS_TRANSITION");
        assertRule(() -> completed.cancel(CancellationType.EARLY, NOW), "INVALID_STATUS_TRANSITION");

        TherapySession noShow = newSession();
        noShow.markNoShow(NOW);
        assertRule(() -> noShow.complete(NOW), "INVALID_STATUS_TRANSITION");

        TherapySession cancelled = newSession();
        cancelled.cancel(CancellationType.LATE, NOW);
        assertRule(() -> cancelled.confirm(NOW), "INVALID_STATUS_TRANSITION");
        assertRule(() -> cancelled.complete(NOW), "INVALID_STATUS_TRANSITION");
    }

    @Test
    @DisplayName("cancelar com antecedência: não cobra; em cima da hora: cobra")
    void cancellationTypeDecidesBilling() {
        TherapySession early = newSession();
        early.cancel(CancellationType.EARLY, NOW);
        TherapySession late = newSession();
        late.cancel(CancellationType.LATE, NOW);

        assertThat(early.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(early.getCancellationType()).isEqualTo(CancellationType.EARLY);
        assertThat(early.isBillable()).isFalse();
        assertThat(late.isBillable()).isTrue();
    }

    @Test
    @DisplayName("cancelar de novo: repetir é inofensivo e outro tipo corrige o tipo")
    void cancelAgainOnlyCorrectsTheType() {
        TherapySession s = newSession();
        s.cancel(CancellationType.EARLY, NOW);

        s.cancel(CancellationType.EARLY, NOW);
        assertThat(s.getCancellationType()).isEqualTo(CancellationType.EARLY);

        s.cancel(CancellationType.LATE, NOW);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(s.getCancellationType()).isEqualTo(CancellationType.LATE);
    }

    @Test
    @DisplayName("pagar: guarda quando foi pago e repetir não muda a data")
    void payingKeepsTheFirstPaymentTime() {
        TherapySession s = newSession();
        Instant first = Instant.parse("2026-09-29T15:00:00Z");

        s.markPaid(first);
        s.markPaid(Instant.parse("2026-09-30T15:00:00Z"));

        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(s.getPaidAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("desfazer pagamento: volta a pendente e apaga a data")
    void undoingPayment() {
        TherapySession s = newSession();
        s.markPaid(NOW);

        s.markPending(NOW);
        s.markPending(NOW);

        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(s.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("sessão não cobrada não recebe pagamento: SESSION_NOT_BILLABLE")
    void notBillableCannotBePaid() {
        TherapySession s = newSession();
        s.cancel(CancellationType.EARLY, NOW);

        assertRule(() -> s.markPaid(NOW), "SESSION_NOT_BILLABLE");
        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("cancelar com antecedência uma sessão já paga mantém o pagamento, e dá para desfazê-lo")
    void cancellingAPaidSessionKeepsThePayment() {
        TherapySession s = newSession();
        s.markPaid(NOW);

        s.cancel(CancellationType.EARLY, NOW);

        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(s.isBillable()).isFalse();
        assertThatCode(() -> s.markPending(NOW)).doesNotThrowAnyException();
        assertThat(s.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
