package br.com.aurora.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Novo e-mail (o login) e a senha atual, para confirmar que é a própria pessoa. */
public record ChangeEmailRequest(
        @NotBlank(message = "Informe o novo e-mail.") @Size(max = 254, message = "No máximo 254 caracteres.")
                String email,
        @NotBlank(message = "Informe a senha atual.") @Size(max = 200, message = "Senha longa demais.")
                String currentPassword) {
}
