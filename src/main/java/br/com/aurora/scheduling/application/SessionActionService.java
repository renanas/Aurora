package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.domain.CancellationType;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ações da psicóloga sobre uma sessão: confirmar, realizada, faltou, cancelar e pagamento.
 * As regras ficam na entidade; aqui só se carrega, se aplica e se devolve a sessão atualizada
 * (a mesma resposta de GET /sessions/{id}, com nome do paciente e avisos de sobreposição).
 */
@Service
public class SessionActionService {

    private final TherapySessionRepository sessions;
    private final SessionQueryService query;
    private final Clock clock;

    public SessionActionService(TherapySessionRepository sessions, SessionQueryService query, Clock clock) {
        this.sessions = sessions;
        this.query = query;
        this.clock = clock;
    }

    @Transactional
    public SessionDetails confirm(UUID id) {
        load(id).confirm(now());
        return query.get(id);
    }

    @Transactional
    public SessionDetails complete(UUID id) {
        load(id).complete(now());
        return query.get(id);
    }

    @Transactional
    public SessionDetails noShow(UUID id) {
        load(id).markNoShow(now());
        return query.get(id);
    }

    /** cancellationType: "EARLY" ou "LATE" (já validado na web). */
    @Transactional
    public SessionDetails cancel(UUID id, String cancellationType) {
        load(id).cancel(CancellationType.valueOf(cancellationType), now());
        return query.get(id);
    }

    /** status: "PAID" ou "PENDING" (já validado na web). */
    @Transactional
    public SessionDetails setPayment(UUID id, String status) {
        TherapySession session = load(id);
        if ("PAID".equals(status)) {
            session.markPaid(now());
        } else {
            session.markPending(now());
        }
        return query.get(id);
    }

    private TherapySession load(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Sessão", id));
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
