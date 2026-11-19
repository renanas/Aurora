package br.com.aurora.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Informe a senha atual.") @Size(max = 200, message = "Senha longa demais.")
                String currentPassword,
        @NotBlank(message = "Informe a nova senha.") @Size(max = 200, message = "Senha longa demais.")
                String newPassword) {
}
