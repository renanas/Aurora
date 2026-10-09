package br.com.aurora.patient.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Publicado quando o valor da sessão do paciente muda de fato. */
public record PatientPriceChanged(UUID patientId, BigDecimal newPrice) {
}
