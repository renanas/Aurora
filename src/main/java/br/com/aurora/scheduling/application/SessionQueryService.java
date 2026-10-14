package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.scheduling.domain.OverlapPolicy;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.error.NotFoundException;
import br.com.aurora.shared.time.SystemConstants;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura da agenda. Sobreposição é aviso, não bloqueio: aqui só se informa quem colide com quem. */
@Service
public class SessionQueryService {

    private final TherapySessionRepository sessions;
    private final PatientLookup patients;

    public SessionQueryService(TherapySessionRepository sessions, PatientLookup patients) {
        this.sessions = sessions;
        this.patients = patients;
    }

    /** Sessões dos dias de {@code from} a {@code to}, inclusive. */
    @Transactional(readOnly = true)
    public List<SessionDetails> list(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > SystemConstants.MAX_AGENDA_RANGE_DAYS) {
            throw new BusinessRuleException("INVALID_DATE_RANGE",
                    "O período deve ter de 1 a " + SystemConstants.MAX_AGENDA_RANGE_DAYS + " dias.");
        }
        List<TherapySession> found = sessions.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return toDetails(found, found);
    }

    @Transactional(readOnly = true)
    public SessionDetails get(UUID id) {
        TherapySession session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Sessão", id));
        // Quem pode colidir começa antes do fim desta sessão e (por folga) no máximo um dia antes do início.
        List<TherapySession> neighbours = sessions.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                session.getStartsAt().minusDays(1), session.endsAt());
        return toDetails(List.of(session), neighbours).getFirst();
    }

    private List<SessionDetails> toDetails(List<TherapySession> targets, List<TherapySession> universe) {
        Set<UUID> patientIds = targets.stream().map(TherapySession::getPatientId).collect(Collectors.toSet());
        Map<UUID, PatientInfo> names = patients.findAllById(patientIds);

        List<OverlapPolicy.Slot> slots = universe.stream()
                .filter(s -> s.getStatus().occupiesSlot())
                .map(s -> new OverlapPolicy.Slot(s.getId(), s.getStartsAt(), s.endsAt()))
                .toList();
        Map<UUID, List<UUID>> overlaps = OverlapPolicy.overlapsBySlot(slots);

        return targets.stream()
                .map(s -> SessionDetails.from(
                        s,
                        names.containsKey(s.getPatientId()) ? names.get(s.getPatientId()).name() : "",
                        overlaps.getOrDefault(s.getId(), List.of())))
                .toList();
    }
}
