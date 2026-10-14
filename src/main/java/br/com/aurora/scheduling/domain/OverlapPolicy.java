package br.com.aurora.scheduling.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regra de sobreposição: início A < fim B e início B < fim A.
 * Sessões que se encostam (uma termina quando a outra começa) NÃO se sobrepõem.
 * Quem chama decide quais sessões entram na conta (só as que ocupam o horário).
 */
public final class OverlapPolicy {

    private OverlapPolicy() {
    }

    public record Slot(UUID id, LocalDateTime start, LocalDateTime end) {
    }

    public static boolean overlaps(LocalDateTime startA, LocalDateTime endA, LocalDateTime startB,
            LocalDateTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /** Para cada slot em conflito, os ids dos outros slots que se sobrepõem a ele. Sem conflito = ausente do mapa. */
    public static Map<UUID, List<UUID>> overlapsBySlot(List<Slot> slots) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot a = slots.get(i);
            for (int j = i + 1; j < slots.size(); j++) {
                Slot b = slots.get(j);
                if (overlaps(a.start(), a.end(), b.start(), b.end())) {
                    result.computeIfAbsent(a.id(), k -> new ArrayList<>()).add(b.id());
                    result.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(a.id());
                }
            }
        }
        return result;
    }
}
