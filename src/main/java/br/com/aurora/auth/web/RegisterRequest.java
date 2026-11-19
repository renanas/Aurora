package br.com.aurora.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro pela própria pessoa (rota pública). A conta fica aguardando aprovação do administrador. */
public record RegisterRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "No máximo 150 caracteres.") String displayName,
        @NotBlank(message = "Informe o e-mail.") @Size(max = 254, message = "No máximo 254 caracteres.") String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "Senha longa demais.")
                String password) {
}
