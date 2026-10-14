package br.com.aurora.patient.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Única porta de entrada do módulo de agenda para dados de paciente. */
public interface PatientLookup {

    Optional<PatientInfo> find(UUID id);

    /** Pacientes inexistentes simplesmente não aparecem no mapa. */
    Map<UUID, PatientInfo> findAllById(Collection<UUID> ids);
}
