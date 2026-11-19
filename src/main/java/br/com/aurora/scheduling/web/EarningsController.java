package br.com.aurora.scheduling.web;

import br.com.aurora.scheduling.application.EarningsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/earnings")
public class EarningsController {

    private final EarningsService service;

    public EarningsController(EarningsService service) {
        this.service = service;
    }

    /**
     * Ganhos de "from" a "to" (inclusive, no máximo 100 dias), pela data da sessão. groupBy = DAY (padrão)
     * ou WEEK (segunda a domingo) define as barras. Só as sessões da psicóloga logada.
     */
    @GetMapping
    public EarningsResponse earnings(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", required = false) String groupBy) {
        return EarningsResponse.from(service.earnings(from, to, groupBy));
    }
}
