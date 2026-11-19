package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AccountDetails;

/**
 * Quem está logada: e-mail (campo "username"), nome para exibir, perfil ("ADMIN" ou "PSYCHOLOGIST") e se
 * ainda precisa trocar a senha provisória. Nada de dados de pacientes aqui.
 */
public record MeResponse(String username, String displayName, String role, boolean mustChangePassword) {

    static MeResponse from(AccountDetails account) {
        return new MeResponse(account.email(), account.displayName(), account.role(), account.mustChangePassword());
    }
}
