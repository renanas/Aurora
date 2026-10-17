package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.scheduling.domain.OverlapPolicy;
import br.com.aurora.scheduling.domain.SessionStatus;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.error.Conflict;
import br.com.aurora.shared.error.NotFoundException;
import br.com.aurora.shared.error.OverlapDetectedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mexe no horário da agenda: sessão avulsa, corrigir horário, reagendar, desfazer e reabrir.
 *
 * Sobreposição é aviso, não bloqueio: se o horário bate com outra sessão e "confirmOverlap" for falso,
 * lança OverlapDetectedException (409); a tela pergunta e reenvia com confirmOverlap = true.
 * A checagem vem depois da mudança na entidade, mas antes de qualquer gravação: o 409 desfaz a transação.
 */
@Service
public class SessionPlanningService {

    private final TherapySessionRepository sessions;
    private final SessionQueryService query;
    private final PatientLookup patients;
    private final Clock clock;

    public SessionPlanningService(TherapySessionRepository sessions, SessionQueryService query,
            PatientLookup patients, Clock clock) {
        this.sessions = sessions;
        this.query = query;
        this.patients = patients;
        this.clock = clock;
    }

    /** Sessão avulsa (fora da recorrência), com o valor atual do paciente. */
    @Transactional
    public SessionDetails create(UUID patientId, LocalDateTime startsAt, boolean confirmOverlap) {
        PatientInfo patient = patients.find(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
        if (!patient.active()) {
            throw new BusinessRuleException("PATIENT_INACTIVE", "O paciente está inativo.");
        }
        TherapySession session = TherapySession.standalone(patientId, startsAt, patient.sessionPrice(), now());
        checkOverlap(startsAt, session.getDurationMinutes(), confirmOverlap, Set.of());
        TherapySession saved = sessions.save(session);
        return query.get(saved.getId());
    }

    /** Corrige o horário de uma sessão AGENDADA (não gera "reagendada"). */
    @Transactional
    public SessionDetails changeTime(UUID id, LocalDateTime newStart, boolean confirmOverlap) {
        TherapySession session = load(id);
        LocalDateTime before = session.getStartsAt();
        session.changeStart(newStart, now());
        if (!newStart.equals(before)) {
            checkOverlap(newStart, session.getDurationMinutes(), confirmOverlap, Set.of(id));
        }
        return query.get(id);
    }

    /** Reagenda: devolve a sessão NOVA. Repetir o mesmo pedido devolve a nova já criada. */
    @Transactional
    public SessionDetails reschedule(UUID id, LocalDateTime newStart, boolean confirmOverlap) {
        TherapySession original = load(id);
        if (original.getStatus() == SessionStatus.RESCHEDULED) {
            Optional<TherapySession> existing =
                    sessions.findFirstByRescheduledFromIdAndStatusNot(id, SessionStatus.CANCELLED);
            if (existing.isPresent() && existing.get().getStartsAt().equals(newStart)) {
                return query.get(existing.get().getId());
            }
        }
        TherapySession next = original.rescheduleTo(newStart, now());
        checkOverlap(newStart, next.getDurationMinutes(), confirmOverlap, Set.of(id));
        TherapySession saved = sessions.save(next);
        return query.get(saved.getId());
    }

    /** Desfaz o reagendamento. O id é o da sessão NOVA; devolve a original, de volta à agenda. */
    @Transactional
    public SessionDetails undoReschedule(UUID id, boolean confirmOverlap) {
        TherapySession replacement = load(id);
        UUID originalId = replacement.getRescheduledFromId();
        if (originalId == null) {
            throw new BusinessRuleException("UNDO_RESCHEDULE_NOT_ALLOWED",
                    "Esta sessão não veio de um reagendamento.");
        }
        TherapySession original = load(originalId);
        boolean alreadyUndone = replacement.getStatus() == SessionStatus.CANCELLED
                && original.getStatus() == SessionStatus.SCHEDULED;
        if (alreadyUndone) {
            return query.get(originalId);
        }
        replacement.undoReschedule(original, now());
        checkOverlap(original.getStartsAt(), original.getDurationMinutes(), confirmOverlap, Set.of(originalId, id));
        return query.get(originalId);
    }

    /** REALIZADA, CANCELADA ou FALTOU -> AGENDADA. Só reabrir uma CANCELADA volta a ocupar o horário. */
    @Transactional
    public SessionDetails reopen(UUID id, boolean confirmOverlap) {
        TherapySession session = load(id);
        SessionStatus before = session.getStatus();
        if (before == SessionStatus.CANCELLED && session.getRescheduledFromId() != null) {
            TherapySession original = load(session.getRescheduledFromId());
            if (original.getStatus() != SessionStatus.RESCHEDULED) {
                throw new BusinessRuleException("REOPEN_NOT_ALLOWED",
                        "Esta sessão foi desfeita de um reagendamento; a original já voltou à agenda.");
            }
        }
        session.reopen(now());
        if (before == SessionStatus.CANCELLED) {
            checkOverlap(session.getStartsAt(), session.getDurationMinutes(), confirmOverlap, Set.of(id));
        }
        return query.get(id);
    }

    // ---------- apoio ----------

    private void checkOverlap(LocalDateTime start, int durationMinutes, boolean confirmed, Set<UUID> ignore) {
        if (confirmed) {
            return;
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        // Quem pode colidir começa antes do fim do novo horário e (por folga) no máximo um dia antes do início.
        List<TherapySession> hits = sessions
                .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(start.minusDays(1), end).stream()
                .filter(s -> s.getStatus().occupiesSlot())
                .filter(s -> !ignore.contains(s.getId()))
                .filter(s -> OverlapPolicy.overlaps(start, end, s.getStartsAt(), s.endsAt()))
                .toList();
        if (hits.isEmpty()) {
            return;
        }
        Set<UUID> patientIds = hits.stream().map(TherapySession::getPatientId).collect(Collectors.toSet());
        Map<UUID, PatientInfo> names = patients.findAllById(patientIds);
        throw new OverlapDetectedException(hits.stream()
                .map(s -> new Conflict(s.getId(),
                        names.containsKey(s.getPatientId()) ? names.get(s.getPatientId()).name() : "",
                        s.getStartsAt(), s.endsAt()))
                .toList());
    }

    private TherapySession load(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Sessão", id));
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
