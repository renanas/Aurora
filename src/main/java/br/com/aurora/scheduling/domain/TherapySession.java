package br.com.aurora.scheduling.domain;

import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.time.SystemConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma sessão concreta na agenda. Referencia o paciente só pelo id (módulos separados).
 * A coluna ends_at do banco é gerada (starts_at + duração): aqui ela é calculada, não mapeada.
 */
@Entity
@Table(name = "therapy_session")
public class TherapySession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "recurrence_id", updatable = false)
    private UUID recurrenceId;

    @Column(name = "occurrence_date", updatable = false)
    private LocalDate occurrenceDate;

    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_type")
    private CancellationType cancellationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TherapySession() {
    }

    /** Sessão gerada a partir de uma recorrência: nasce agendada e com pagamento pendente. */
    public static TherapySession generated(UUID patientId, UUID recurrenceId, LocalDate occurrenceDate,
            LocalDateTime startsAt, BigDecimal amount, Instant now) {
        TherapySession s = new TherapySession();
        s.patientId = patientId;
        s.recurrenceId = recurrenceId;
        s.occurrenceDate = occurrenceDate;
        s.startsAt = startsAt;
        s.durationMinutes = (short) SystemConstants.SESSION_DURATION_MINUTES;
        s.status = SessionStatus.SCHEDULED;
        s.paymentStatus = PaymentStatus.PENDING;
        s.amount = amount;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    public LocalDateTime endsAt() {
        return startsAt.plusMinutes(durationMinutes);
    }

    /** Gera cobrança? Canceladas com antecedência e reagendadas não. */
    public boolean isBillable() {
        return switch (status) {
            case SCHEDULED, CONFIRMED, COMPLETED, NO_SHOW -> true;
            case CANCELLED -> cancellationType == CancellationType.LATE;
            case RESCHEDULED -> false;
        };
    }

    public void changeAmount(BigDecimal newAmount, Instant now) {
        this.amount = newAmount;
        this.updatedAt = now;
    }

    // ---------- ações da sessão (entrega C) ----------
    // Repetir uma ação já aplicada não muda nada (idempotente). Sem regra de horário: como na folha de papel,
    // a psicóloga pode marcar "realizada" ou "faltou" quando quiser.

    /** AGENDADA -> CONFIRMADA. */
    public void confirm(Instant now) {
        if (status == SessionStatus.CONFIRMED) {
            return;
        }
        requireOpen();
        status = SessionStatus.CONFIRMED;
        updatedAt = now;
    }

    /** AGENDADA ou CONFIRMADA -> REALIZADA. */
    public void complete(Instant now) {
        if (status == SessionStatus.COMPLETED) {
            return;
        }
        requireOpen();
        status = SessionStatus.COMPLETED;
        updatedAt = now;
    }

    /** AGENDADA ou CONFIRMADA -> FALTOU. A sessão continua devida. */
    public void markNoShow(Instant now) {
        if (status == SessionStatus.NO_SHOW) {
            return;
        }
        requireOpen();
        status = SessionStatus.NO_SHOW;
        updatedAt = now;
    }

    /**
     * AGENDADA ou CONFIRMADA -> CANCELADA, com o tipo. Já cancelada: só corrige o tipo, se for outro.
     * O pagamento NÃO é mexido (se já estava pago, continua pago: a tela avisa).
     */
    public void cancel(CancellationType type, Instant now) {
        Objects.requireNonNull(type, "type");
        if (status == SessionStatus.CANCELLED) {
            if (cancellationType != type) {
                cancellationType = type;
                updatedAt = now;
            }
            return;
        }
        requireOpen();
        status = SessionStatus.CANCELLED;
        cancellationType = type;
        updatedAt = now;
    }

    /** Marca como paga. Sessão que não gera cobrança não recebe pagamento. */
    public void markPaid(Instant now) {
        if (paymentStatus == PaymentStatus.PAID) {
            return;
        }
        if (!isBillable()) {
            throw new BusinessRuleException("SESSION_NOT_BILLABLE",
                    "Esta sessão não é cobrada, então não recebe pagamento.");
        }
        paymentStatus = PaymentStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    /** Desfaz o pagamento. Sempre permitido (serve para corrigir um engano, inclusive em sessão não cobrada). */
    public void markPending(Instant now) {
        if (paymentStatus == PaymentStatus.PENDING) {
            return;
        }
        paymentStatus = PaymentStatus.PENDING;
        paidAt = null;
        updatedAt = now;
    }

    // ---------- agenda (entrega D) ----------

    /** Sessão avulsa: fora de qualquer recorrência. Nasce agendada e com pagamento pendente. */
    public static TherapySession standalone(UUID patientId, LocalDateTime startsAt, BigDecimal amount, Instant now) {
        TherapySession s = new TherapySession();
        s.patientId = patientId;
        s.startsAt = startsAt;
        s.durationMinutes = (short) SystemConstants.SESSION_DURATION_MINUTES;
        s.status = SessionStatus.SCHEDULED;
        s.paymentStatus = PaymentStatus.PENDING;
        s.amount = amount;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /**
     * Corrige o horário de uma sessão AGENDADA (erro de digitação, sem gerar "reagendada").
     * occurrence_date não muda: a sessão continua sendo a daquela data da recorrência.
     */
    public void changeStart(LocalDateTime newStart, Instant now) {
        if (status != SessionStatus.SCHEDULED) {
            throw new BusinessRuleException("SESSION_NOT_SCHEDULED",
                    "Só é possível alterar o horário de uma sessão agendada.");
        }
        if (newStart.equals(startsAt)) {
            return;
        }
        startsAt = newStart;
        updatedAt = now;
    }

    /**
     * Reagenda: esta sessão vira REAGENDADA e devolve-se a nova (AGENDADA, ligada a esta).
     * A nova recebe valor, pagamento e data de pagamento; esta fica sem pagamento (ele "mudou de lugar").
     * A recorrência não é tocada. A nova ainda precisa ser salva por quem chamou.
     */
    public TherapySession rescheduleTo(LocalDateTime newStart, Instant now) {
        requireOpen();
        if (newStart.equals(startsAt)) {
            throw new BusinessRuleException("RESCHEDULE_SAME_TIME", "Escolha um horário diferente do atual.");
        }
        TherapySession next = new TherapySession();
        next.patientId = patientId;
        next.rescheduledFromId = id;
        next.startsAt = newStart;
        next.durationMinutes = durationMinutes;
        next.status = SessionStatus.SCHEDULED;
        next.paymentStatus = paymentStatus;
        next.paidAt = paidAt;
        next.amount = amount;
        next.createdAt = now;
        next.updatedAt = now;

        status = SessionStatus.RESCHEDULED;
        paymentStatus = PaymentStatus.PENDING;
        paidAt = null;
        updatedAt = now;
        return next;
    }

    /**
     * Desfaz o reagendamento. Chamado na sessão NOVA (ainda AGENDADA): a original volta a AGENDADA,
     * recebe de volta o pagamento, e esta vira CANCELADA com antecedência (sem cobrança).
     */
    public void undoReschedule(TherapySession original, Instant now) {
        boolean allowed = rescheduledFromId != null
                && rescheduledFromId.equals(original.id)
                && status == SessionStatus.SCHEDULED
                && original.status == SessionStatus.RESCHEDULED;
        if (!allowed) {
            throw new BusinessRuleException("UNDO_RESCHEDULE_NOT_ALLOWED",
                    "Só dá para desfazer um reagendamento enquanto a nova sessão ainda está agendada.");
        }
        original.status = SessionStatus.SCHEDULED;
        original.paymentStatus = paymentStatus;
        original.paidAt = paidAt;
        original.updatedAt = now;

        status = SessionStatus.CANCELLED;
        cancellationType = CancellationType.EARLY;
        paymentStatus = PaymentStatus.PENDING;
        paidAt = null;
        updatedAt = now;
    }

    /** REALIZADA, CANCELADA ou FALTOU -> AGENDADA, limpando o tipo de cancelamento. Já agendada: nada muda. */
    public void reopen(Instant now) {
        switch (status) {
            case SCHEDULED -> {
                return;
            }
            case COMPLETED, CANCELLED, NO_SHOW -> {
                status = SessionStatus.SCHEDULED;
                cancellationType = null;
                updatedAt = now;
            }
            default -> throw new BusinessRuleException("REOPEN_NOT_ALLOWED",
                    "Só é possível reabrir uma sessão realizada, cancelada ou com falta.");
        }
    }

    private void requireOpen() {
        if (status != SessionStatus.SCHEDULED && status != SessionStatus.CONFIRMED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Esta ação não é possível na situação atual da sessão.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getRecurrenceId() {
        return recurrenceId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public UUID getRescheduledFromId() {
        return rescheduledFromId;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public CancellationType getCancellationType() {
        return cancellationType;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
