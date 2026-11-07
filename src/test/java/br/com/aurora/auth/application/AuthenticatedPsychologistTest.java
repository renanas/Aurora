package br.com.aurora.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedPsychologistTest {

    private final AuthenticatedPsychologist current = new AuthenticatedPsychologist();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sem conta autenticada, falha em vez de devolver um dono qualquer")
    void failsWithoutAuthentication() {
        assertThatThrownBy(current::id).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("usuário anônimo também falha")
    void failsForAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "chave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(current::id).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("com conta autenticada, devolve o id dela")
    void returnsTheLoggedInAccountId() {
        AppUser user = AppUser.create("ana@aurora.test", "hash", "Ana", Role.PSYCHOLOGIST, false, Instant.now());
        AuroraUserDetails details = AuroraUserDetails.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));

        assertThat(current.id()).isEqualTo(user.id());
    }
}
