package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aurora.scheduling.domain.OverlapPolicy.Slot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OverlapPolicyTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 28, hour, minute);
    }

    private static Slot slot(UUID id, int hour, int minute) {
        LocalDateTime start = at(hour, minute);
        return new Slot(id, start, start.plusMinutes(50));
    }

    @Test
    @DisplayName("sessões que se encostam (uma termina quando a outra começa) não se sobrepõem")
    void touchingDoesNotOverlap() {
        assertThat(OverlapPolicy.overlaps(at(9, 0), at(9, 50), at(9, 50), at(10, 40))).isFalse();
        assertThat(OverlapPolicy.overlaps(at(9, 50), at(10, 40), at(9, 0), at(9, 50))).isFalse();
    }

    @Test
    @DisplayName("sobreposição parcial e total são detectadas nos dois sentidos")
    void partialAndFullOverlap() {
        assertThat(OverlapPolicy.overlaps(at(9, 0), at(9, 50), at(9, 30), at(10, 20))).isTrue();
        assertThat(OverlapPolicy.overlaps(at(9, 30), at(10, 20), at(9, 0), at(9, 50))).isTrue();
        assertThat(OverlapPolicy.overlaps(at(9, 0), at(9, 50), at(9, 0), at(9, 50))).isTrue();
        assertThat(OverlapPolicy.overlaps(at(9, 0), at(11, 0), at(9, 30), at(10, 0))).isTrue();
    }

    @Test
    @DisplayName("o mapa é simétrico e só traz quem tem conflito")
    void mapIsSymmetricAndOnlyHasConflicts() {
        UUID firstSlotId = UUID.randomUUID();
        UUID secondSlotId = UUID.randomUUID();
        UUID thirdSlotId = UUID.randomUUID();

        Map<UUID, List<UUID>> result = OverlapPolicy.overlapsBySlot(
                List.of(slot(firstSlotId, 9, 0), slot(secondSlotId, 9, 30), slot(thirdSlotId, 11, 0)));

        assertThat(result).containsOnlyKeys(firstSlotId, secondSlotId);
        assertThat(result.get(firstSlotId)).containsExactly(secondSlotId);
        assertThat(result.get(secondSlotId)).containsExactly(firstSlotId);
    }

    @Test
    @DisplayName("três sessões no mesmo horário: cada uma conflita com as outras duas")
    void threeAtTheSameTime() {
        UUID firstSlotId = UUID.randomUUID();
        UUID secondSlotId = UUID.randomUUID();
        UUID thirdSlotId = UUID.randomUUID();

        Map<UUID, List<UUID>> result = OverlapPolicy.overlapsBySlot(
                List.of(slot(firstSlotId, 9, 0), slot(secondSlotId, 9, 0), slot(thirdSlotId, 9, 0)));

        assertThat(result.get(firstSlotId)).containsExactlyInAnyOrder(secondSlotId, thirdSlotId);
        assertThat(result.get(secondSlotId)).containsExactlyInAnyOrder(firstSlotId, thirdSlotId);
        assertThat(result.get(thirdSlotId)).containsExactlyInAnyOrder(firstSlotId, secondSlotId);
    }

    @Test
    @DisplayName("sem sessões, sem conflitos")
    void empty() {
        assertThat(OverlapPolicy.overlapsBySlot(List.of())).isEmpty();
    }
}
