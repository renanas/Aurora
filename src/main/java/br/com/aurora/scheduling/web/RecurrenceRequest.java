package br.com.aurora.scheduling.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * anchorDate: a data da primeira sessão (define também o dia da semana).
 * startTime: "HH:mm", sem segundos. frequency: WEEKLY ou BIWEEKLY (texto, para a web não depender do domínio).
 */
public record RecurrenceRequest(
        @NotNull(message = "Informe a data da primeira sessão.")
        LocalDate anchorDate,

        @NotBlank(message = "Informe o horário.")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Informe o horário no formato HH:mm.")
        String startTime,

        @NotBlank(message = "Informe a frequência.")
        @Pattern(regexp = "^(WEEKLY|BIWEEKLY)$", message = "Escolha semanal ou quinzenal.")
        String frequency) {
}
