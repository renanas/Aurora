package br.com.aurora.shared.error;

/**
 * Base dos erros previstos. Carrega um código estável (usado pelo frontend).
 * Mensagens NUNCA podem conter nome, telefone ou outro dado pessoal: só IDs e códigos.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
