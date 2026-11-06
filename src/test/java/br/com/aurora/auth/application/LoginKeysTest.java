package br.com.aurora.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginKeysTest {

    @Test
    @DisplayName("ignora maiúsculas e espaços nas pontas")
    void normalizes() {
        assertThat(LoginKeys.of("  Ana@Aurora.TEST ")).isEqualTo("ana@aurora.test");
    }

    @Test
    @DisplayName("nulo vira chave vazia")
    void nullBecomesEmpty() {
        assertThat(LoginKeys.of(null)).isEmpty();
    }

    @Test
    @DisplayName("texto enorme é cortado")
    void truncates() {
        assertThat(LoginKeys.of("a".repeat(5000))).hasSize(LoginKeys.MAX_LENGTH);
    }
}
