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

    @Test
    @DisplayName("ativar de novo uma conta desativada")
    void activate() {
        AppUser user = ana();
        user.deactivate();

        user.activate();

        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("trocar a própria senha grava o novo hash e tira a obrigação de trocar")
    void changePasswordClearsTheFlag() {
        AppUser user = ana(); // nasce com troca obrigatória

        user.changePassword("hash-novo");

        assertThat(user.passwordHash()).isEqualTo("hash-novo");
        assertThat(user.mustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("redefinir a senha grava o hash provisório e obriga a trocar")
    void resetPasswordForcesTheChange() {
        AppUser user = AppUser.create("bia@aurora.test", "hash", "Bia", Role.PSYCHOLOGIST, false, NOW);

        user.resetPassword("hash-provisorio");

        assertThat(user.passwordHash()).isEqualTo("hash-provisorio");
        assertThat(user.mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("trocar ou redefinir exige um hash")
    void newHashIsRequired() {
        assertThatThrownBy(() -> ana().changePassword(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ana().resetPassword(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("conta criada diretamente já nasce aprovada")
    void createdAccountIsApproved() {
        assertThat(ana().isApproved()).isTrue();
        assertThat(ana().canLogIn()).isTrue();
    }

    @Test
    @DisplayName("cadastro pela própria pessoa: psicóloga, normalizada, sem troca obrigatória e aguardando aprovação")
    void registerWaitsForApproval() {
        AppUser user = AppUser.register(" Bia@Aurora.Test ", "hash", " Bia ", NOW);

        assertThat(user.email()).isEqualTo("bia@aurora.test");
        assertThat(user.displayName()).isEqualTo("Bia");
        assertThat(user.role()).isEqualTo(Role.PSYCHOLOGIST);
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(user.isActive()).isTrue();
        assertThat(user.isApproved()).isFalse();
        assertThat(user.canLogIn()).isFalse();
    }

    @Test
    @DisplayName("aprovar libera a entrada; desativada continua sem entrar")
    void approveAllowsLogin() {
        AppUser user = AppUser.register("bia@aurora.test", "hash", "Bia", NOW);

        user.approve();
        assertThat(user.canLogIn()).isTrue();

        user.deactivate();
        assertThat(user.canLogIn()).isFalse();
    }

    @Test
    @DisplayName("cadastro valida os dados como a criação")
    void registerValidates() {
        assertThatThrownBy(() -> AppUser.register("sem-arroba", "hash", "Bia", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppUser.register("bia@aurora.test", "hash", "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
