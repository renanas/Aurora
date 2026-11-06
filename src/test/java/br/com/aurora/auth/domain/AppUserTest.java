package br.com.aurora.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    private static AppUser ana() {
        return AppUser.create("  Ana@Aurora.TEST ", "hash", "  Dra. Ana  ", Role.PSYCHOLOGIST, true, NOW);
    }

    @Test
    @DisplayName("conta nova: e-mail e nome normalizados, ativa, com id")
    void createNormalizes() {
        AppUser user = ana();

        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo("ana@aurora.test");
        assertThat(user.displayName()).isEqualTo("Dra. Ana");
        assertThat(user.role()).isEqualTo(Role.PSYCHOLOGIST);
        assertThat(user.isActive()).isTrue();
        assertThat(user.mustChangePassword()).isTrue();
        assertThat(user.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("e-mail inválido é recusado")
    void rejectsInvalidEmail() {
        for (String bad : new String[] {null, "", "   ", "sem-arroba", "@x.com", "a@", "a b@x.com", "a@@x.com"}) {
            assertThatThrownBy(() -> AppUser.create(bad, "hash", "Ana", Role.ADMIN, false, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("e-mail longo demais é recusado")
    void rejectsLongEmail() {
        String longEmail = "a".repeat(250) + "@x.com";

        assertThatThrownBy(() -> AppUser.create(longEmail, "hash", "Ana", Role.ADMIN, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nome vazio ou longo demais é recusado")
    void rejectsInvalidName() {
        assertThatThrownBy(() -> AppUser.create("a@x.com", "hash", "   ", Role.ADMIN, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppUser.create("a@x.com", "hash", "n".repeat(151), Role.ADMIN, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hash de senha é obrigatório")
    void requiresPasswordHash() {
        assertThatThrownBy(() -> AppUser.create("a@x.com", " ", "Ana", Role.ADMIN, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mensagem de erro não revela o e-mail digitado")
    void errorDoesNotEchoEmail() {
        assertThatThrownBy(() -> AppUser.create("segredo sem arroba", "hash", "Ana", Role.ADMIN, false, NOW))
                .hasMessageNotContaining("segredo");
    }

    @Test
    @DisplayName("desativar deixa a conta inativa")
    void deactivate() {
        AppUser user = ana();

        user.deactivate();

        assertThat(user.isActive()).isFalse();
    }
}
