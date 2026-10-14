package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.scheduling.domain.OccurrenceCalculator;
import br.com.aurora.scheduling.domain.Recurrence;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.RecurrenceRepository;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.time.SystemConstants;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializa as sessões de uma recorrência até o horizonte (8 semanas).
 * Idempotente: só cria as datas que ainda não existem (e o banco garante com UNIQUE).
 * Sessões apagadas pela psicóloga nunca "voltam" porque ela não apaga: cancela, falta, reagenda.
 */
@Service
public class SessionGenerator {

    private final RecurrenceRepository recurrences;
    private final TherapySessionRepository sessions;
    private final PatientLookup patients;
    private final Clock clock;

    public SessionGenerator(RecurrenceRepository recurrences, TherapySessionRepository sessions,
            PatientLookup patients, Clock clock) {
        this.recurrences = recurrences;
        this.sessions = sessions;
        this.patients = patients;
        this.clock = clock;
    }

    /** Gera para uma recorrência pelo id, em transação própria (usado pelo job diário). */
    @Transactional
    public int generateFor(UUID recurrenceId) {
        Optional<Recurrence> recurrence = recurrences.findById(recurrenceId);
        return recurrence.map(this::generate).orElse(0);
    }

    /** Gera para uma recorrência já carregada. Deve ser chamado dentro de uma transação. */
    public int generate(Recurrence recurrence) {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizonEnd = today.plusWeeks(SystemConstants.GENERATION_HORIZON_WEEKS);

        // Começa em max(âncora, hoje): o calculador já nunca devolve datas antes da âncora.
        List<LocalDate> dates = OccurrenceCalculator.occurrences(
                recurrence.getAnchorDate(), recurrence.getFrequency(), today, horizonEnd, recurrence.getEndsOn());
        if (dates.isEmpty()) {
            return 0;
        }

        Optional<PatientInfo> patient = patients.find(recurrence.getPatientId());
        if (patient.isEmpty() || !patient.get().active()) {
            return 0;
        }

        Set<LocalDate> existing = new HashSet<>(
                sessions.findOccurrenceDates(recurrence.getId(), dates.getFirst(), dates.getLast()));
        Instant now = Instant.now(clock);

        List<TherapySession> created = dates.stream()
                .filter(date -> !existing.contains(date))
                .map(date -> TherapySession.generated(
                        recurrence.getPatientId(), recurrence.getId(), date,
                        date.atTime(recurrence.getStartTime()), patient.get().sessionPrice(), now))
                .toList();
        sessions.saveAll(created);
        return created.size();
    }
}
