package br.com.aurora.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** Relógio que só anda quando o teste manda. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    MutableClock clock;
    LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new LoginAttemptLimiter(clock, 5, Duration.ofMinutes(15));
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure();
        }
    }

    @Test
    @DisplayName("começa liberado")
    void startsUnlocked() {
        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("abaixo do limite continua liberado")
    void belowLimitStaysUnlocked() {
        fail(4);

        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("na quinta falha seguida bloqueia pelo tempo configurado")
    void locksAfterMaxFailures() {
        fail(5);

        assertThat(limiter.remainingLock()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("o tempo de bloqueio diminui e o login volta depois dele")
    void lockExpires() {
        fail(5);

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.remainingLock()).isEqualTo(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("depois que o bloqueio acaba, a contagem recomeça do zero")
    void countRestartsAfterLock() {
        fail(5);
        clock.advance(Duration.ofMinutes(15));

        fail(4);

        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("login bem-sucedido zera as falhas")
    void successResetsFailures() {
        fail(4);
        limiter.recordSuccess();

        fail(4);

        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("falhas antigas (além da janela) são esquecidas")
    void oldFailuresAreForgotten() {
        fail(4);
        clock.advance(Duration.ofMinutes(15));

        fail(1);

        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("falha durante o bloqueio não estende o bloqueio")
    void failureWhileLockedDoesNotExtend() {
        fail(5);
        clock.advance(Duration.ofMinutes(5));

        fail(3);

        assertThat(limiter.remainingLock()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("recordSuccess também libera um bloqueio (usado para reiniciar o estado)")
    void successClearsLock() {
        fail(5);

        limiter.recordSuccess();

        assertThat(limiter.remainingLock()).isZero();
    }

    @Test
    @DisplayName("rejeita configuração inválida")
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
