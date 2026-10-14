package br.com.aurora.scheduling.application;

import br.com.aurora.patient.api.PatientDeactivated;
import br.com.aurora.patient.api.PatientPriceChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reage a eventos do módulo de pacientes. São síncronos: rodam na mesma transação de quem publicou,
 * então "inativar o paciente" e "encerrar o horário dele" acontecem juntos ou não acontecem.
 */
@Component
public class PatientEventListeners {

    private final RecurrenceService recurrenceService;
    private final SessionPriceService priceService;

    public PatientEventListeners(RecurrenceService recurrenceService, SessionPriceService priceService) {
        this.recurrenceService = recurrenceService;
        this.priceService = priceService;
    }

    @EventListener
    public void onPatientDeactivated(PatientDeactivated event) {
        recurrenceService.endIfActive(event.patientId());
    }

    @EventListener
    public void onPatientPriceChanged(PatientPriceChanged event) {
        priceService.applyNewPrice(event.patientId(), event.newPrice());
    }
}
