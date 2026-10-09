package br.com.aurora.patient.persistence;

import br.com.aurora.patient.domain.Patient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /**
     * Busca por nome ignorando maiúsculas e acentos. O pattern já vem com curingas (%) e com os
     * caracteres especiais escapados com a barra invertida (veja PatientService.toLikePattern).
     */
    @Query(value = """
            SELECT * FROM patient
            WHERE active = :active
              AND unaccent(lower(name)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
            ORDER BY unaccent(lower(name)), name
            """, nativeQuery = true)
    List<Patient> searchByName(@Param("active") boolean active, @Param("pattern") String pattern);
}
