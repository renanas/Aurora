package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;
import java.time.Instant;
import java.util.UUID;

/** Dados de uma conta para a tela de administração. Nunca inclui senha nem hash. */
public record AccountDetails(UUID id, String email, String displayName, String role, boolean active,
        boolean mustChangePassword, boolean approved, Instant createdAt) {

    static AccountDetails from(AppUser user) {
        return new AccountDetails(user.id(), user.email(), user.displayName(), user.role().name(),
                user.isActive(), user.mustChangePassword(), user.isApproved(), user.createdAt());
    }
}
