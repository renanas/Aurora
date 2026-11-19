package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.domain.Recurrence;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** O que a camada web pode saber de uma recorrência. A entidade não sai desta camada. */
public record RecurrenceDetails(UUID id, UUID patientId, LocalDate anchorDate, LocalTime startTime,
        String frequency, LocalDate endsOn) {

    static RecurrenceDetails from(Recurrence recurrence) {
        return new RecurrenceDetails(recurrence.getId(), recurrence.getPatientId(), recurrence.getAnchorDate(), recurrence.getStartTime(),
                recurrence.getFrequency().name(), recurrence.getEndsOn());
    }
}
