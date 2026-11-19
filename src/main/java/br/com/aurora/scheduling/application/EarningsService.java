package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.scheduling.domain.EarningsCalculator;
import br.com.aurora.scheduling.domain.EarningsCalculator.Entry;
import br.com.aurora.scheduling.domain.EarningsCalculator.GroupBy;
import br.com.aurora.scheduling.domain.EarningsCalculator.Result;
import br.com.aurora.scheduling.persistence.TherapySessionRepository;
import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.security.CurrentPsychologist;
import br.com.aurora.shared.time.SystemConstants;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ganhos da psicóloga logada num período (pela data da sessão). O cálculo é o EarningsCalculator. */
@Service
public class EarningsService {

    private final TherapySessionRepository sessions;
    private final PatientLookup patients;
    private final CurrentPsychologist current;
    private final Clock clock;

    public EarningsService(TherapySessionRepository sessions, PatientLookup patients, CurrentPsychologist current,
            Clock clock) {
        this.sessions = sessions;
        this.patients = patients;
        this.current = current;
        this.clock = clock;
    }

    /** Dias de {@code from} a {@code to}, inclusive (no máximo 100), agrupados por "DAY" ou "WEEK". */
    @Transactional(readOnly = true)
    public EarningsDetails earnings(LocalDate from, LocalDate to, String groupBy) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > SystemConstants.MAX_AGENDA_RANGE_DAYS) {
            throw new BusinessRuleException("INVALID_DATE_RANGE",
                    "O período deve ter de 1 a " + SystemConstants.MAX_AGENDA_RANGE_DAYS + " dias.");
        }
        GroupBy grouping = parseGroupBy(groupBy);
        UUID owner = current.id();

        List<Entry> entries = sessions.findInRange(owner, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().map(Entry::of).toList();
        LocalDateTime now = LocalDateTime.now(clock);
        Result result = EarningsCalculator.calculate(entries, from, to, grouping, now);

        Set<UUID> patientIds = result.receivableEntries().stream().map(Entry::patientId).collect(Collectors.toSet());
        Map<UUID, PatientInfo> names = patients.findAllById(owner, patientIds);
        LocalDate horizon = now.toLocalDate().plusWeeks(SystemConstants.GENERATION_HORIZON_WEEKS);

        return new EarningsDetails(
                from,
                to,
                grouping.name(),
                result.received(),
                result.receivable(),
                result.expected(),
                result.total(),
                result.paidNotBillableAmount(),
                result.paidNotBillableCount(),
                result.sessionsWithoutOutcome(),
                to.isAfter(horizon),
                result.periods().stream()
                        .map(period -> new EarningsDetails.Period(period.start(), period.end(), period.received(), period.receivable(),
                                period.expected()))
                        .toList(),
                result.receivableEntries().stream()
                        .map(entry -> new EarningsDetails.ReceivableSession(entry.sessionId(), entry.patientId(),
                                names.containsKey(entry.patientId()) ? names.get(entry.patientId()).name() : "",
                                entry.startsAt(), entry.status().name(), entry.amount()))
                        .toList());
    }

    private static GroupBy parseGroupBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return GroupBy.DAY;
        }
        return switch (raw) {
            case "DAY" -> GroupBy.DAY;
            case "WEEK" -> GroupBy.WEEK;
            default -> throw new BusinessRuleException("INVALID_GROUP_BY", "Agrupe por DAY ou WEEK.");
        };
    }
}
