package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.RecurrenceDetails;
import br.com.aurora.scheduling.application.RecurrenceService;
import jakarta.validation.Valid;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients/{patientId}/recurrence")
public class RecurrenceController {

    private final RecurrenceService service;

    public RecurrenceController(RecurrenceService service) {
        this.service = service;
    }

    /** 200 com o horário ativo, ou 204 se o paciente não tem horário recorrente. */
    @GetMapping
    public ResponseEntity<RecurrenceResponse> get(@PathVariable("patientId") UUID patientId) {
        Optional<RecurrenceDetails> active = service.getActive(patientId);
        if (active.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(RecurrenceResponse.from(active.get()));
    }

    /** Define (ou troca) o horário recorrente a partir da data informada. */
    @PutMapping
    public RecurrenceResponse replace(@PathVariable("patientId") UUID patientId,
            @Valid @RequestBody RecurrenceRequest request) {
        return RecurrenceResponse.from(service.replace(
                patientId, request.anchorDate(), LocalTime.parse(request.startTime()), request.frequency()));
    }

    @PostMapping("/end")
    public ResponseEntity<Void> end(@PathVariable("patientId") UUID patientId) {
        service.end(patientId);
        return ResponseEntity.noContent().build();
    }
}
