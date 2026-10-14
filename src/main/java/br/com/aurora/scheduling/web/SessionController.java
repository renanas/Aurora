package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.SessionQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionQueryService service;

    public SessionController(SessionQueryService service) {
        this.service = service;
    }

    /** Sessões dos dias de "from" a "to" (inclusive), no máximo 100 dias. Datas no formato 2026-09-28. */
    @GetMapping
    public List<SessionResponse> list(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to).stream().map(SessionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable("id") UUID id) {
        return SessionResponse.from(service.get(id));
    }
}
