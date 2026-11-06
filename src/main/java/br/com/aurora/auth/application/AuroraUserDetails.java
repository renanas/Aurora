package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Quem está logada, como o Spring Security enxerga. Guarda o id da conta (a F2 usa para isolar os
 * dados por psicóloga) e o nome para exibir. Conta desativada aparece como não habilitada e não
 * entra. O hash da senha é apagado daqui depois do login.
 */
public final class AuroraUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String roleName;
    private final boolean enabled;
    private String passwordHash;

    private AuroraUserDetails(UUID id, String email, String passwordHash, String displayName, String roleName,
            boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roleName = roleName;
        this.enabled = enabled;
    }

    static AuroraUserDetails from(AppUser user) {
        return new AuroraUserDetails(user.id(), user.email(), user.passwordHash(), user.displayName(),
                user.role().name(), user.isActive());
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Nome do perfil ("ADMIN" ou "PSYCHOLOGIST"), como texto para quem não conhece o domínio. */
    public String roleName() {
        return roleName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
