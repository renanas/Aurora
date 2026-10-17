package br.com.aurora.scheduling.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** startsAt: "2026-09-28T09:00" (sem segundos e sem fuso). confirmOverlap: reenvio depois do aviso de conflito. */
public record CreateSessionRequest(
        @NotNull(message = "Informe o paciente.")
        UUID patientId,

        @NotBlank(message = "Informe a data e o horário.")
        @Pattern(regexp = RequestDateTimes.PATTERN, message = "Informe a data e o horário no formato 2026-09-28T09:00.")
        String startsAt,

        boolean confirmOverlap) {
}
