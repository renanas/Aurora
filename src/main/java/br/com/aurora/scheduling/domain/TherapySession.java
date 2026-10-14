package br.com.aurora.scheduling.domain;

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
}
