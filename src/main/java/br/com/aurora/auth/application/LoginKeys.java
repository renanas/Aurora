package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;

/**
 * Chave do limitador de tentativas: o e-mail digitado, normalizado como o da conta ("ANA@x.com "
 * e "ana@x.com" contam juntos) e cortado para ninguém encher a memória com textos enormes.
 */
public final class LoginKeys {

    static final int MAX_LENGTH = 254;

    private LoginKeys() {
    }

    public static String of(String rawUsername) {
        String key = AppUser.normalizeEmail(rawUsername);
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH) : key;
    }
}
