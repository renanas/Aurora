package br.com.aurora.patient.application;

import br.com.aurora.patient.domain.Patient;
import java.math.BigDecimal;
import java.util.UUID;

/** O que a camada web pode saber de um paciente. A entidade não sai desta camada. */
public record PatientDetails(UUID id, String name, String whatsapp, BigDecimal sessionPrice, boolean active) {

    static PatientDetails from(Patient patient) {
        return new PatientDetails(patient.getId(), patient.getName(), patient.getWhatsapp(), patient.getSessionPrice(), patient.isActive());
    }
}
