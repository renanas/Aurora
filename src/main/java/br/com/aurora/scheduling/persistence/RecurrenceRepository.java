package br.com.aurora.scheduling.persistence;

import br.com.aurora.scheduling.domain.Recurrence;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceRepository extends JpaRepository<Recurrence, UUID> {

    Optional<Recurrence> findByPatientIdAndEndsOnIsNull(UUID patientId);

    /** Recorrências que ainda geram sessões: ativas, ou encerradas com data de fim ainda no futuro. */
    @Query("select r.id from Recurrence r where r.endsOn is null or r.endsOn >= :today")
    List<UUID> findGeneratingIds(@Param("today") LocalDate today);
}
