package br.com.aurora.patient.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Única porta de entrada do módulo de agenda para dados de paciente. Sempre por dona: paciente de outra
 * psicóloga é como se não existisse (o job das 03:00 passa a dona gravada na recorrência).
 */
public interface PatientLookup {

    Optional<PatientInfo> find(UUID psychologistId, UUID id);

    /** Pacientes inexistentes simplesmente não aparecem no mapa. */
    Map<UUID, PatientInfo> findAllById(UUID psychologistId, Collection<UUID> ids);
}
