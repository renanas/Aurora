package br.com.aurora.scheduling.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** status: PAID (marcar como pago) ou PENDING (desfazer o pagamento). */
public record PaymentRequest(
        @NotBlank(message = "Informe a situação do pagamento.")
        @Pattern(regexp = "^(PAID|PENDING)$", message = "Escolha pago ou pendente.")
        String status) {
}
