package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.SessionActionService;
import br.com.aurora.scheduling.application.SessionPlanningService;
import br.com.aurora.scheduling.application.SessionQueryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionQueryService service;
    private final SessionActionService actions;
    private final SessionPlanningService planning;

    public SessionController(SessionQueryService service, SessionActionService actions,
            SessionPlanningService planning) {
        this.service = service;
        this.actions = actions;
        this.planning = planning;
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

    // ---------- ações (todas idempotentes: repetir devolve 200 com o estado atual) ----------

    @PostMapping("/{id}/confirm")
    public SessionResponse confirm(@PathVariable("id") UUID id) {
        return SessionResponse.from(actions.confirm(id));
    }

    @PostMapping("/{id}/complete")
    public SessionResponse complete(@PathVariable("id") UUID id) {
        return SessionResponse.from(actions.complete(id));
    }

    @PostMapping("/{id}/no-show")
    public SessionResponse noShow(@PathVariable("id") UUID id) {
        return SessionResponse.from(actions.noShow(id));
    }

    @PostMapping("/{id}/cancel")
    public SessionResponse cancel(@PathVariable("id") UUID id, @Valid @RequestBody CancelRequest request) {
        return SessionResponse.from(actions.cancel(id, request.cancellationType()));
    }

    @PutMapping("/{id}/payment")
    public SessionResponse payment(@PathVariable("id") UUID id, @Valid @RequestBody PaymentRequest request) {
        return SessionResponse.from(actions.setPayment(id, request.status()));
    }

    // ---------- agenda (entrega D) ----------

    /** Sessão avulsa. 409 OVERLAP_DETECTED se bater com outra; reenviar com confirmOverlap = true. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
        return SessionResponse.from(planning.create(
                request.patientId(), RequestDateTimes.parse(request.startsAt()), request.confirmOverlap()));
    }

    /** Corrige o horário de uma sessão agendada (sem gerar "reagendada"). */
    @PatchMapping("/{id}/time")
    public SessionResponse changeTime(@PathVariable("id") UUID id, @Valid @RequestBody StartRequest request) {
        return SessionResponse.from(planning.changeTime(
                id, RequestDateTimes.parse(request.startsAt()), request.confirmOverlap()));
    }

    /** Reagenda. Devolve a sessão NOVA. */
    @PostMapping("/{id}/reschedule")
    public SessionResponse reschedule(@PathVariable("id") UUID id, @Valid @RequestBody StartRequest request) {
        return SessionResponse.from(planning.reschedule(
                id, RequestDateTimes.parse(request.startsAt()), request.confirmOverlap()));
    }

    /** Desfaz o reagendamento. O id é o da sessão NOVA; devolve a original. */
    @PostMapping("/{id}/undo-reschedule")
    public SessionResponse undoReschedule(@PathVariable("id") UUID id,
            @Valid @RequestBody ConfirmOverlapRequest request) {
        return SessionResponse.from(planning.undoReschedule(id, request.confirmOverlap()));
    }

    @PostMapping("/{id}/reopen")
    public SessionResponse reopen(@PathVariable("id") UUID id, @Valid @RequestBody ConfirmOverlapRequest request) {
        return SessionResponse.from(planning.reopen(id, request.confirmOverlap()));
    }
}
