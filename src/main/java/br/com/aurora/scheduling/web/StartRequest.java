package br.com.aurora.scheduling.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Novo horário de uma sessão (corrigir ou reagendar). */
public record StartRequest(
        @NotBlank(message = "Informe a data e o horário.")
        @Pattern(regexp = RequestDateTimes.PATTERN, message = "Informe a data e o horário no formato 2026-09-28T09:00.")
        String startsAt,

        boolean confirmOverlap) {
}
