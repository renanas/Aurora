package br.com.aurora.patient.application;

import br.com.aurora.patient.domain.Patient;
import java.math.BigDecimal;
import java.util.UUID;

/** O que a camada web pode saber de um paciente. A entidade não sai desta camada. */
public record PatientDetails(UUID id, String name, String whatsapp, BigDecimal sessionPrice, boolean active) {

    static PatientDetails from(Patient p) {
        return new PatientDetails(p.getId(), p.getName(), p.getWhatsapp(), p.getSessionPrice(), p.isActive());
    }
}
