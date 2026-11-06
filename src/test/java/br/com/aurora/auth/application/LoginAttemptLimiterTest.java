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

    private static final String ANA = "ana@aurora.test";
    private static final String BIA = "bia@aurora.test";

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
        limiter = new LoginAttemptLimiter(clock, 5, Duration.ofMinutes(15), 1000);
    }

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(key);
        }
    }

    @Test
    @DisplayName("começa liberado")
    void startsUnlocked() {
        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("abaixo do limite continua liberado")
    void belowLimitStaysUnlocked() {
        fail(ANA, 4);

        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("na quinta falha seguida bloqueia pelo tempo configurado")
    void locksAfterMaxFailures() {
        fail(ANA, 5);

        assertThat(limiter.remainingLock(ANA)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("o bloqueio de um e-mail não atinge os outros")
    void keysAreIndependent() {
        fail(ANA, 5);

        assertThat(limiter.remainingLock(ANA)).isPositive();
        assertThat(limiter.remainingLock(BIA)).isZero();

        fail(BIA, 4);
        assertThat(limiter.remainingLock(BIA)).isZero();
    }

    @Test
    @DisplayName("o tempo de bloqueio diminui e o login volta depois dele")
    void lockExpires() {
        fail(ANA, 5);

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.remainingLock(ANA)).isEqualTo(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("depois que o bloqueio acaba, a contagem recomeça do zero")
    void countRestartsAfterLock() {
        fail(ANA, 5);
        clock.advance(Duration.ofMinutes(15));

        fail(ANA, 4);

        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("login bem-sucedido zera as falhas daquele e-mail")
    void successResetsFailures() {
        fail(ANA, 4);
        limiter.recordSuccess(ANA);

        fail(ANA, 4);

        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("falhas antigas (além da janela) são esquecidas")
    void oldFailuresAreForgotten() {
        fail(ANA, 4);
        clock.advance(Duration.ofMinutes(15));

        fail(ANA, 1);

        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("falha durante o bloqueio não estende o bloqueio")
    void failureWhileLockedDoesNotExtend() {
        fail(ANA, 5);
        clock.advance(Duration.ofMinutes(5));

        fail(ANA, 3);

        assertThat(limiter.remainingLock(ANA)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("recordSuccess também libera um bloqueio")
    void successClearsLock() {
        fail(ANA, 5);

        limiter.recordSuccess(ANA);

        assertThat(limiter.remainingLock(ANA)).isZero();
    }

    @Test
    @DisplayName("reset esquece todos os e-mails")
    void resetForgetsEverything() {
        fail(ANA, 5);
        fail(BIA, 5);

        limiter.reset();

        assertThat(limiter.remainingLock(ANA)).isZero();
        assertThat(limiter.remainingLock(BIA)).isZero();
    }

    @Test
    @DisplayName("com a memória cheia, descarta primeiro quem não está bloqueado e mantém os bloqueios")
    void fullMemoryEvictsUnlockedFirst() {
        LoginAttemptLimiter small = new LoginAttemptLimiter(clock, 5, Duration.ofMinutes(15), 3);
        for (int i = 0; i < 5; i++) {
            small.recordFailure("a");   // a: bloqueado (o mais antigo)
        }
        small.recordFailure("b");        // b: 1 falha
        small.recordFailure("c");        // c: 1 falha  (a, b, c: cheio)

        small.recordFailure("d");        // precisa de espaço: sai b (o não bloqueado mais antigo)

        assertThat(small.remainingLock("a")).isPositive();
        // b foi esquecido: 4 falhas novas não bastam para bloquear (se lembrasse da anterior, seriam 5)
        for (int i = 0; i < 4; i++) {
            small.recordFailure("b");
        }
        assertThat(small.remainingLock("b")).isZero();
    }

    @Test
    @DisplayName("com a memória cheia, o que já expirou sai antes de qualquer outro")
    void fullMemoryDropsExpiredFirst() {
        LoginAttemptLimiter small = new LoginAttemptLimiter(clock, 5, Duration.ofMinutes(15), 2);
        for (int i = 0; i < 5; i++) {
            small.recordFailure("a");   // a: bloqueado até +15 min
        }
        clock.advance(Duration.ofMinutes(10));
        small.recordFailure("b");        // b: 1 falha, recente (a, b: cheio)
        clock.advance(Duration.ofMinutes(6)); // a expirou; b ainda vale

        small.recordFailure("c");        // sai a (expirado); b fica

        for (int i = 0; i < 4; i++) {
            small.recordFailure("b");
        }
        assertThat(small.remainingLock("b")).isPositive(); // b lembrava da 1ª falha: 1 + 4 = 5
    }

    @Test
    @DisplayName("rejeita configuração inválida")
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 0, Duration.ofMinutes(1), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 3, Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 3, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
