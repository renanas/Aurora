package br.com.aurora.patient.api;

import java.util.UUID;

/** Publicado quando um paciente é inativado (uma única vez por inativação). */
public record PatientDeactivated(UUID patientId) {
}
