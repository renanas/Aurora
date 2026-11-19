package br.com.aurora.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Informe a senha provisória.") @Size(max = 200, message = "Senha longa demais.")
                String temporaryPassword) {
}
