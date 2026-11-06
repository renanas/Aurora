package br.com.aurora.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limite de tentativas de login por chave (o e-mail normalizado, ver {@link LoginKeys}): depois de
 * {@code maxFailures} falhas seguidas, o login daquela chave fica bloqueado por {@code lockDuration}.
 * Falhas mais antigas que {@code lockDuration} são esquecidas.
 *
 * É por e-mail (e não global) para que alguém de fora não consiga travar todas as contas. E-mail
 * que não existe é tratado exatamente como um que existe: o bloqueio não revela quais contas há.
 * Não é por IP: atrás de um proxy todos os IPs pareceriam o mesmo. O segundo fator (TOTP) é o passo
 * seguinte para endurecer isso.
 *
 * Estado em memória (reiniciar a aplicação zera os contadores), com no máximo {@code maxEntries}
 * chaves: ao encher, descarta primeiro o que já expirou, depois as chaves sem bloqueio mais antigas
 * e só em último caso a mais antiga de todas. Classe pura (sem Spring) para ser testada com um
 * relógio controlado.
 */
public class LoginAttemptLimiter {

    private static final class State {
        int failures;
        Instant lastFailureAt;
        Instant lockedUntil;
    }

    private final Clock clock;
    private final int maxFailures;
    private final Duration lockDuration;
    private final int maxEntries;

    private final Map<String, State> states = new LinkedHashMap<>();

    public LoginAttemptLimiter(Clock clock, int maxFailures, Duration lockDuration, int maxEntries) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures deve ser pelo menos 1");
        }
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration deve ser positiva");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries deve ser pelo menos 1");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.maxEntries = maxEntries;
    }

    /** Tempo que ainda falta de bloqueio para a chave; {@link Duration#ZERO} se está liberada. */
    public synchronized Duration remainingLock(String key) {
        State state = states.get(key);
        if (state == null || state.lockedUntil == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        if (!now.isBefore(state.lockedUntil)) {
            states.remove(key); // bloqueio acabou; a contagem já tinha sido zerada ao bloquear
            return Duration.ZERO;
        }
        return Duration.between(now, state.lockedUntil);
    }

    public synchronized void recordFailure(String key) {
        if (!remainingLock(key).isZero()) {
            return; // já bloqueado: não estende o bloqueio
        }
        Instant now = clock.instant();
        State state = states.get(key);
        if (state != null && Duration.between(state.lastFailureAt, now).compareTo(lockDuration) >= 0) {
            state.failures = 0; // falhas antigas são esquecidas
        }
        if (state == null) {
            makeRoom(now);
            state = new State();
            states.put(key, state);
        }
        state.failures++;
        state.lastFailureAt = now;
        if (state.failures >= maxFailures) {
            state.lockedUntil = now.plus(lockDuration);
            state.failures = 0;
        }
    }

    /** Login bem-sucedido: zera contador e bloqueio da chave. */
    public synchronized void recordSuccess(String key) {
        states.remove(key);
    }

    /** Esquece tudo (usado pelos testes, que compartilham o bean). */
    public synchronized void reset() {
        states.clear();
    }

    private void makeRoom(Instant now) {
        if (states.size() < maxEntries) {
            return;
        }
        states.values().removeIf(state -> isExpired(state, now));
        if (states.size() < maxEntries) {
            return;
        }
        Iterator<State> unlocked = states.values().iterator();
        while (states.size() >= maxEntries && unlocked.hasNext()) {
            if (unlocked.next().lockedUntil == null) {
                unlocked.remove();
            }
        }
        if (states.size() >= maxEntries) {
            Iterator<String> oldest = states.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private boolean isExpired(State state, Instant now) {
        if (state.lockedUntil != null) {
            return !now.isBefore(state.lockedUntil);
        }
        return Duration.between(state.lastFailureAt, now).compareTo(lockDuration) >= 0;
    }
}
