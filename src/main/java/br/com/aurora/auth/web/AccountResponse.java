package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AccountDetails;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String email, String displayName, String role, boolean active,
        boolean mustChangePassword, boolean approved, Instant createdAt) {

    static AccountResponse from(AccountDetails account) {
        return new AccountResponse(account.id(), account.email(), account.displayName(), account.role(),
                account.active(), account.mustChangePassword(), account.approved(), account.createdAt());
    }
}
