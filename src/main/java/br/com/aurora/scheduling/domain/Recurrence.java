package br.com.aurora.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Horário habitual de um paciente. Não muda: trocar o horário é encerrar esta e criar outra.
 * Ativa enquanto {@code endsOn} é nulo; {@code endsOn} é o último dia em que ainda gera sessões.
 */
@Entity
@Table(name = "recurrence")
public class Recurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "anchor_date", nullable = false, updatable = false)
    private LocalDate anchorDate;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Frequency frequency;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Recurrence() {
    }

    public Recurrence(UUID patientId, LocalDate anchorDate, LocalTime startTime, Frequency frequency, Instant now) {
        this.patientId = patientId;
        this.anchorDate = anchorDate;
        this.startTime = startTime;
        this.frequency = frequency;
        this.createdAt = now;
    }

    public boolean isActive() {
        return endsOn == null;
    }

    public boolean hasSameSchedule(LocalDate anchorDate, LocalTime startTime, Frequency frequency) {
        return this.anchorDate.equals(anchorDate)
                && this.startTime.equals(startTime)
                && this.frequency == frequency;
    }

    /** Define o último dia de vigência. Nunca antes da primeira ocorrência (o banco também exige). */
    public void endOn(LocalDate lastDay) {
        if (lastDay.isBefore(anchorDate)) {
            throw new IllegalArgumentException("O fim da vigência não pode ser antes da âncora.");
        }
        this.endsOn = lastDay;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }
}
