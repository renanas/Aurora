package br.com.aurora.scheduling.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** cancellationType: EARLY (sem cobrança) ou LATE (com cobrança). Texto, para a web não depender do domínio. */
public record CancelRequest(
        @NotBlank(message = "Informe o tipo de cancelamento.")
        @Pattern(regexp = "^(EARLY|LATE)$", message = "Escolha cancelamento com antecedência ou em cima da hora.")
        String cancellationType) {
}
