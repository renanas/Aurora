package br.com.aurora.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    @DisplayName("o relógio do sistema usa o fuso de São Paulo")
    void clockUsesSaoPauloZone() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    @DisplayName("constantes acordadas: 50 minutos e horizonte de 8 semanas")
    void agreedConstants() {
        assertThat(SystemConstants.SESSION_DURATION_MINUTES).isEqualTo(50);
        assertThat(SystemConstants.GENERATION_HORIZON_WEEKS).isEqualTo(8);
    }
}
