package br.com.aurora.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aurora.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    /** Código do erro de negócio que a senha provoca, ou null se ela é aceita. */
    private static String codeOf(String password) {
        try {
            PasswordPolicy.validate(password);
            return null;
        } catch (BusinessRuleException rejected) {
            return rejected.code();
        }
    }

    @Test
    @DisplayName("12 caracteres é o mínimo")
    void minimumLength() {
        assertThat(codeOf("a".repeat(11))).isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(codeOf("a".repeat(12))).isNull();
    }

    @Test
    @DisplayName("senha nula ou vazia é curta demais")
    void nullAndEmpty() {
        assertThat(codeOf(null)).isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(codeOf("")).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    @DisplayName("72 bytes é o máximo (limite do BCrypt)")
    void maximumBytes() {
        assertThat(codeOf("a".repeat(72))).isNull();
        assertThat(codeOf("a".repeat(73))).isEqualTo("PASSWORD_TOO_LONG");
    }

    @Test
    @DisplayName("o limite conta bytes, não letras: acentos ocupam mais de um")
    void accentedCharactersCountAsBytes() {
        assertThat(codeOf("é".repeat(36))).isNull();      // 72 bytes
        assertThat(codeOf("é".repeat(37))).isEqualTo("PASSWORD_TOO_LONG"); // 74 bytes
    }

    @Test
    @DisplayName("emoji conta como 1 caractere no mínimo, mas 4 bytes no máximo")
    void emojiCountsAsOneCharacter() {
        assertThat(codeOf("😀".repeat(12))).isNull();     // 12 caracteres, 48 bytes
        assertThat(codeOf("😀".repeat(19))).isEqualTo("PASSWORD_TOO_LONG"); // 76 bytes
    }

    @Test
    @DisplayName("espaços fazem parte da senha")
    void spacesCount() {
        assertThat(codeOf(" ".repeat(12))).isNull();
    }

    @Test
    @DisplayName("a mensagem de erro não revela a senha")
    void messageDoesNotEchoThePassword() {
        try {
            PasswordPolicy.validate("segredo");
        } catch (BusinessRuleException rejected) {
            assertThat(rejected.getMessage()).doesNotContain("segredo");
        }
    }
}
