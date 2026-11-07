package br.com.aurora.patient.persistence;

import br.com.aurora.patient.domain.Patient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /** Paciente só da psicóloga dada: de outra, é como se não existisse. */
    Optional<Patient> findByIdAndPsychologistId(UUID id, UUID psychologistId);

    List<Patient> findByIdInAndPsychologistId(Collection<UUID> ids, UUID psychologistId);

    /**
     * Busca por nome ignorando maiúsculas e acentos. O pattern já vem com curingas (%) e com os
     * caracteres especiais escapados com a barra invertida (veja PatientService.toLikePattern).
     */
    @Query(value = """
            SELECT * FROM patient
            WHERE psychologist_id = :psychologistId
              AND active = :active
              AND unaccent(lower(name)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
            ORDER BY unaccent(lower(name)), name
            """, nativeQuery = true)
    List<Patient> searchByName(@Param("psychologistId") UUID psychologistId, @Param("active") boolean active,
            @Param("pattern") String pattern);
}
