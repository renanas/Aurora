package br.com.aurora.patient.web;

import br.com.aurora.patient.application.PatientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public List<PatientResponse> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "active", defaultValue = "true") boolean active) {
        return service.list(search, active).stream().map(PatientResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        PatientResponse created = PatientResponse.from(
                service.create(request.name(), request.whatsapp(), request.sessionPrice()));
        return ResponseEntity.created(URI.create("/api/patients/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable("id") UUID id) {
        return PatientResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public PatientResponse update(@PathVariable("id") UUID id, @Valid @RequestBody PatientRequest request) {
        return PatientResponse.from(
                service.update(id, request.name(), request.whatsapp(), request.sessionPrice()));
    }

    @PostMapping("/{id}/deactivate")
    public PatientResponse deactivate(@PathVariable("id") UUID id) {
        return PatientResponse.from(service.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    public PatientResponse activate(@PathVariable("id") UUID id) {
        return PatientResponse.from(service.activate(id));
    }
}
