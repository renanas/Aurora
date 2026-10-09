package br.com.aurora.patient.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PatientRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
        String name,

        // Aceita qualquer formatação (+55 (11) 99999-8888); exige de 10 a 15 dígitos. O servidor guarda só os dígitos.
        @NotBlank(message = "Informe o WhatsApp.")
        @Size(max = 40, message = "WhatsApp inválido.")
        @Pattern(regexp = "^(\\D*\\d){10,15}\\D*$", message = "O WhatsApp deve ter de 10 a 15 dígitos.")
        String whatsapp,

        @NotNull(message = "Informe o valor da sessão.")
        @DecimalMin(value = "0.00", message = "O valor não pode ser negativo.")
        @Digits(integer = 8, fraction = 2, message = "Valor inválido (use até 2 casas decimais).")
        BigDecimal sessionPrice) {
}
