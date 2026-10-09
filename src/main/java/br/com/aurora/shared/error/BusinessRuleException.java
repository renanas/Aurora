package br.com.aurora.shared.error;

/**
 * Regra de negócio violada que confirmar não resolve (ex.: INVALID_STATUS_TRANSITION).
 * Vira 422 com o código informado. A mensagem é mostrada ao cliente: sem dados pessoais.
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
