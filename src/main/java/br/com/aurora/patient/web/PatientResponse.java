package br.com.aurora.patient.web;

import br.com.aurora.patient.application.PatientDetails;
import java.math.BigDecimal;
import java.util.UUID;

public record PatientResponse(UUID id, String name, String whatsapp, BigDecimal sessionPrice, boolean active) {

    static PatientResponse from(PatientDetails details) {
        return new PatientResponse(details.id(), details.name(), details.whatsapp(), details.sessionPrice(), details.active());
    }
}
