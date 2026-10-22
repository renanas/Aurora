package br.com.aurora.auth.web;

import br.com.aurora.auth.application.LoginAttemptLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recusa tentativas de login (429) enquanto o limite de tentativas estiver bloqueando, sem nem
 * verificar a senha. Fica antes do filtro de login do Spring Security.
 * Não é um bean: é criado dentro da SecurityConfig para não ser registrado duas vezes.
 */
final class LoginThrottleFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";

    private final LoginAttemptLimiter limiter;

    LoginThrottleFilter(LoginAttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI())) {
            Duration remaining = limiter.remainingLock();
            if (!remaining.isZero()) {
                long seconds = (remaining.toMillis() + 999) / 1000;
                long minutes = Math.max(1, (seconds + 59) / 60);
                response.setHeader("Retry-After", String.valueOf(seconds));
                JsonProblemWriter.write(response, 429, "Muitas tentativas", "TOO_MANY_ATTEMPTS",
                        "Muitas tentativas de entrada. Tente novamente em " + minutes
                                + (minutes == 1 ? " minuto." : " minutos."));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
