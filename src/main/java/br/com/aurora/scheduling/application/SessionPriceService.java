package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.domain.PaymentStatus;
import br.com.aurora.scheduling.domain.SessionStatus;
import br.com.aurora.scheduling.domain.TherapySession;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quando o valor do paciente muda, só sessões futuras, AGENDADAS ou CONFIRMADAS e com pagamento PENDENTE acompanham. */
@Service
public class SessionPriceService {

    private final TherapySessionRepository sessions;
    private final Clock clock;

    public SessionPriceService(TherapySessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public int applyNewPrice(UUID patientId, BigDecimal newPrice) {
        List<TherapySession> targets = sessions.findPriceUpdatable(
                patientId, LocalDateTime.now(clock),
                List.of(SessionStatus.SCHEDULED, SessionStatus.CONFIRMED), PaymentStatus.PENDING);
        Instant now = Instant.now(clock);
        targets.forEach(s -> s.changeAmount(newPrice, now));
        return targets.size();
    }
}
