package br.com.aurora.auth.domain;

import br.com.aurora.shared.error.BusinessRuleException;
import java.nio.charset.StandardCharsets;

/**
 * Regra de senha: pelo menos 12 caracteres e no máximo 72 bytes em UTF-8 (limite do BCrypt, que
 * ignoraria o resto sem avisar; acentos e emojis ocupam mais de 1 byte). Espaços nas pontas contam e
 * não são removidos. As mensagens não incluem a senha.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new BusinessRuleException("PASSWORD_TOO_SHORT",
                    "A senha deve ter pelo menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessRuleException("PASSWORD_TOO_LONG",
                    "A senha é longa demais (no máximo " + MAX_BYTES + " bytes; acentos contam mais de um).");
        }
    }
}
