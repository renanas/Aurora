package br.com.aurora.patient.domain;

import br.com.aurora.shared.error.BusinessRuleException;

/**
 * Número de WhatsApp: só dígitos, de 10 a 15. Qualquer outro caractere digitado é descartado.
 * Com 10 ou 11 dígitos (DDD + número) é um número brasileiro sem o código do país: ganha o prefixo 55.
 */
public record Whatsapp(String digits) {

    private static final String BRAZIL_PREFIX = "55";

    public static Whatsapp of(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 15) {
            throw new BusinessRuleException("INVALID_WHATSAPP", "O WhatsApp deve ter de 10 a 15 dígitos.");
        }
        if (digits.length() <= 11) {
            digits = BRAZIL_PREFIX + digits;
        }
        return new Whatsapp(digits);
    }
}
