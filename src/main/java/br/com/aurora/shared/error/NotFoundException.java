package br.com.aurora.shared.error;

import java.util.UUID;

/** Recurso inexistente. Vira 404 NOT_FOUND. */
public class NotFoundException extends DomainException {

    public NotFoundException(String resource, UUID id) {
        super("NOT_FOUND", resource + " não encontrado(a): " + id);
    }
}
