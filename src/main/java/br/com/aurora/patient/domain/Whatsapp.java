package br.com.aurora.patient.domain;

import br.com.aurora.shared.error.BusinessRuleException;

/** Número de WhatsApp: só dígitos, de 10 a 15. Qualquer outro caractere digitado é descartado. */
public record Whatsapp(String digits) {

    public static Whatsapp of(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 15) {
            throw new BusinessRuleException("INVALID_WHATSAPP", "O WhatsApp deve ter de 10 a 15 dígitos.");
        }
        return new Whatsapp(digits);
    }
}
