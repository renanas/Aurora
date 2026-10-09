package br.com.aurora.patient.application;

import br.com.aurora.patient.api.PatientDeactivated;
import br.com.aurora.patient.api.PatientPriceChanged;
import br.com.aurora.patient.domain.Patient;
import br.com.aurora.patient.persistence.PatientRepository;
import br.com.aurora.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private static final int MAX_SEARCH_LENGTH = 100;

    private final PatientRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PatientService(PatientRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PatientDetails> list(String search, boolean active) {
        return repository.searchByName(active, toLikePattern(search)).stream()
                .map(PatientDetails::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientDetails get(UUID id) {
        return PatientDetails.from(find(id));
    }

    @Transactional
    public PatientDetails create(String name, String whatsapp, BigDecimal sessionPrice) {
        Patient patient = new Patient(name, whatsapp, sessionPrice, now());
        return PatientDetails.from(repository.save(patient));
    }

    @Transactional
    public PatientDetails update(UUID id, String name, String whatsapp, BigDecimal sessionPrice) {
        Patient patient = find(id);
        boolean priceChanged = patient.update(name, whatsapp, sessionPrice, now());
        if (priceChanged) {
            events.publishEvent(new PatientPriceChanged(patient.getId(), patient.getSessionPrice()));
        }
        return PatientDetails.from(patient);
    }

    /** Idempotente: inativar quem já está inativo não faz nada e não publica evento. */
    @Transactional
    public PatientDetails deactivate(UUID id) {
        Patient patient = find(id);
        if (patient.deactivate(now())) {
            events.publishEvent(new PatientDeactivated(patient.getId()));
        }
        return PatientDetails.from(patient);
    }

    /** Idempotente. Não restaura recorrência: ela define uma nova, se quiser. */
    @Transactional
    public PatientDetails activate(UUID id) {
        Patient patient = find(id);
        patient.activate(now());
        return PatientDetails.from(patient);
    }

    /** Transforma o texto digitado em um padrão LIKE seguro: %e%s%c%a%p%a%d%o% -> literais escapados. */
    static String toLikePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String trimmed = search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEARCH_LENGTH);
        }
        String escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private Patient find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Paciente", id));
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
