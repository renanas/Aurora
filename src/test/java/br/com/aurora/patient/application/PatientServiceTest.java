package br.com.aurora.patient.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientServiceTest {

    @Test
    @DisplayName("sem texto de busca, o padrão casa tudo")
    void blankSearchMatchesEverything() {
        assertThat(PatientService.toLikePattern(null)).isEqualTo("%");
        assertThat(PatientService.toLikePattern("   ")).isEqualTo("%");
    }

    @Test
    @DisplayName("texto comum vira busca por trecho, sem espaços nas pontas")
    void plainTextBecomesContainsSearch() {
        assertThat(PatientService.toLikePattern("  mar ")).isEqualTo("%mar%");
    }

    @Test
    @DisplayName("%, _ e barra digitados são tratados como texto, não como curinga")
    void specialCharactersAreEscaped() {
        assertThat(PatientService.toLikePattern("50%")).isEqualTo("%50\\%%");
        assertThat(PatientService.toLikePattern("a_b")).isEqualTo("%a\\_b%");
        assertThat(PatientService.toLikePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    @DisplayName("busca gigante é cortada em 100 caracteres")
    void longSearchIsTruncated() {
        String pattern = PatientService.toLikePattern("x".repeat(500));

        assertThat(pattern).isEqualTo("%" + "x".repeat(100) + "%");
    }
}
