package br.com.aurora.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Limite de tentativas de login: depois de {@code maxFailures} falhas seguidas, o login fica bloqueado
 * por {@code lockDuration}. Falhas mais antigas que {@code lockDuration} são esquecidas.
 *
 * O bloqueio é global (não por IP): o sistema tem uma única usuária e, atrás de um proxy, todos os
 * IPs pareceriam o mesmo. Custo aceito: quem insistir de fora pode travar o login dela por alguns
 * minutos; o segundo fator (TOTP) é o passo seguinte para endurecer isso.
 *
 * Estado em memória: reiniciar a aplicação zera o contador. Classe pura (sem Spring) para ser
 * testada com um relógio controlado.
 */
public class LoginAttemptLimiter {

    private final Clock clock;
    private final int maxFailures;
    private final Duration lockDuration;

    private int failures;
    private Instant lastFailureAt;
    private Instant lockedUntil;

    public LoginAttemptLimiter(Clock clock, int maxFailures, Duration lockDuration) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures deve ser pelo menos 1");
        }
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration deve ser positiva");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    /** Tempo que ainda falta de bloqueio; {@link Duration#ZERO} se o login está liberado. */
    public synchronized Duration remainingLock() {
        if (lockedUntil == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        if (!now.isBefore(lockedUntil)) {
            lockedUntil = null;
            return Duration.ZERO;
        }
        return Duration.between(now, lockedUntil);
    }

    public synchronized void recordFailure() {
        if (!remainingLock().isZero()) {
            return; // já bloqueado: não estende o bloqueio
        }
        Instant now = clock.instant();
        if (lastFailureAt != null && Duration.between(lastFailureAt, now).compareTo(lockDuration) >= 0) {
            failures = 0;
        }
        failures++;
        lastFailureAt = now;
        if (failures >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failures = 0;
        }
    }

    /** Login bem-sucedido (ou reinício do estado): zera contador e bloqueio. */
    public synchronized void recordSuccess() {
        failures = 0;
        lastFailureAt = null;
        lockedUntil = null;
    }
}
