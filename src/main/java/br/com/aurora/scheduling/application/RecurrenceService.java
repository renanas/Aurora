package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.scheduling.domain.Frequency;
import br.com.aurora.scheduling.domain.PaymentStatus;
import br.com.aurora.scheduling.domain.Recurrence;
import br.com.aurora.scheduling.domain.SessionStatus;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.RecurrenceRepository;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Horário habitual do paciente. Trocar = encerrar a atual e criar outra (no máximo uma ativa por paciente).
 *
 * Ao encerrar ou trocar, apaga-se somente o que ainda não aconteceu e ninguém tocou: sessão gerada,
 * futura, AGENDADA, com pagamento PENDENTE e no horário original. Todo o resto fica como está.
 */
@Service
public class RecurrenceService {

    private final RecurrenceRepository recurrences;
    private final TherapySessionRepository sessions;
    private final PatientLookup patients;
    private final SessionGenerator generator;
    private final Clock clock;

    public RecurrenceService(RecurrenceRepository recurrences, TherapySessionRepository sessions,
            PatientLookup patients, SessionGenerator generator, Clock clock) {
        this.recurrences = recurrences;
        this.sessions = sessions;
        this.patients = patients;
        this.generator = generator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<RecurrenceDetails> getActive(UUID patientId) {
        requirePatient(patientId);
        return recurrences.findByPatientIdAndEndsOnIsNull(patientId).map(RecurrenceDetails::from);
    }

    /**
     * Define o horário recorrente a partir de {@code anchorDate}. Repetir exatamente o mesmo horário não faz nada.
     */
    @Transactional
    public RecurrenceDetails replace(UUID patientId, LocalDate anchorDate, LocalTime startTime, String frequencyName) {
        PatientInfo patient = requirePatient(patientId);
        if (!patient.active()) {
            throw new BusinessRuleException("PATIENT_INACTIVE", "O paciente está inativo.");
        }
        Frequency frequency = Frequency.valueOf(frequencyName);

        Optional<Recurrence> current = recurrences.findByPatientIdAndEndsOnIsNull(patientId);
        if (current.isPresent()) {
            Recurrence old = current.get();
            if (old.hasSameSchedule(anchorDate, startTime, frequency)) {
                return RecurrenceDetails.from(old);
            }
            retire(old, anchorDate, anchorDate.minusDays(1));
        }

        Recurrence created = recurrences.save(
                new Recurrence(patientId, anchorDate, startTime, frequency, Instant.now(clock)));
        generator.generate(created);
        return RecurrenceDetails.from(created);
    }

    @Transactional
    public void end(UUID patientId) {
        requirePatient(patientId);
        Recurrence active = recurrences.findByPatientIdAndEndsOnIsNull(patientId)
                .orElseThrow(() -> new BusinessRuleException("NO_ACTIVE_RECURRENCE",
                        "O paciente não tem horário recorrente ativo."));
        endToday(active);
    }

    /** Usado ao inativar o paciente: se não havia recorrência, não há nada a fazer. */
    @Transactional
    public void endIfActive(UUID patientId) {
        recurrences.findByPatientIdAndEndsOnIsNull(patientId).ifPresent(this::endToday);
    }

    private void endToday(Recurrence recurrence) {
        LocalDate today = LocalDate.now(clock);
        LocalDate yesterday = today.minusDays(1);
        // Nunca antes da âncora (recorrência que ainda nem começou): se sobrar sessão, a vigência é só o dia da âncora.
        LocalDate lastDay = yesterday.isBefore(recurrence.getAnchorDate()) ? recurrence.getAnchorDate() : yesterday;
        retire(recurrence, today, lastDay);
    }

    /**
     * Remove as sessões descartáveis a partir de {@code fromDate}; depois apaga a recorrência (se não sobrou
     * nenhuma sessão) ou fixa o último dia de vigência.
     */
    private void retire(Recurrence recurrence, LocalDate fromDate, LocalDate lastDay) {
        LocalDateTime now = LocalDateTime.now(clock);

        List<TherapySession> disposable = sessions
                .findFutureUntouched(recurrence.getId(), fromDate, now, SessionStatus.SCHEDULED, PaymentStatus.PENDING)
                .stream()
                .filter(s -> s.getStartsAt().equals(s.getOccurrenceDate().atTime(recurrence.getStartTime())))
                .toList();
        sessions.deleteAll(disposable);
        sessions.flush();

        if (sessions.countByRecurrenceId(recurrence.getId()) == 0) {
            recurrences.delete(recurrence);
        } else {
            if (lastDay.isBefore(recurrence.getAnchorDate())) {
                throw new BusinessRuleException("RECURRENCE_ANCHOR_NOT_AFTER_CURRENT",
                        "A nova data deve ser posterior ao início do horário atual.");
            }
            recurrence.endOn(lastDay);
        }
        // O banco só admite uma recorrência ativa por paciente: efetivar isto antes de criar a próxima.
        recurrences.flush();
    }

    private PatientInfo requirePatient(UUID patientId) {
        return patients.find(patientId).orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }
}
