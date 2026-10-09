package br.com.aurora.shared.error;

import java.util.List;

/**
 * Sobreposição de horário que a psicóloga pode confirmar (reenvio com confirmOverlap=true).
 * Vira 409 OVERLAP_DETECTED com a lista de conflitos.
 */
public class OverlapDetectedException extends DomainException {

    private final transient List<Conflict> conflicts;

    public OverlapDetectedException(List<Conflict> conflicts) {
        super("OVERLAP_DETECTED", "Horário em conflito com " + conflicts.size() + " sessão(ões).");
        this.conflicts = List.copyOf(conflicts);
    }

    public List<Conflict> conflicts() {
        return conflicts;
    }
}
