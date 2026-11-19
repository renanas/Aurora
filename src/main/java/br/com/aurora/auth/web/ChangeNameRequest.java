package br.com.aurora.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeNameRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "No máximo 150 caracteres.")
                String displayName) {
}
