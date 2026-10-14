package br.com.aurora.patient.application;

import br.com.aurora.patient.api.PatientInfo;
import br.com.aurora.patient.api.PatientLookup;
import br.com.aurora.patient.domain.Patient;
import br.com.aurora.patient.persistence.PatientRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientLookupService implements PatientLookup {

    private final PatientRepository repository;

    public PatientLookupService(PatientRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientInfo> find(UUID id) {
        return repository.findById(id).map(PatientLookupService::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PatientInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PatientInfo> result = new HashMap<>();
        for (Patient patient : repository.findAllById(ids)) {
            result.put(patient.getId(), toInfo(patient));
        }
        return result;
    }

    private static PatientInfo toInfo(Patient p) {
        return new PatientInfo(p.getId(), p.getName(), p.getSessionPrice(), p.isActive());
    }
}
