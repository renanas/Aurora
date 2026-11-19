package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AccountService;
import br.com.aurora.auth.application.AccountState;
import br.com.aurora.auth.application.AuroraUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A cada requisição de quem está logada, reconfere a conta no banco (consulta por chave primária):
 *  - conta desativada ou apagada: a sessão acaba na hora (401), sem esperar o fim do cookie;
 *  - senha provisória pendente: só /api/auth/me e /api/auth/password funcionam; o resto responde 403
 *    PASSWORD_CHANGE_REQUIRED. Vale no servidor, não só na tela.
 * Fica antes da autorização. Não é um bean: é criado dentro da SecurityConfig.
 */
final class SessionAccountFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_PASSWORD_PENDING =
            Set.of("GET /api/auth/me", "POST /api/auth/password");

    private final AccountService accounts;

    SessionAccountFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuroraUserDetails user
                && request.getRequestURI().startsWith("/api/")) {

            Optional<AccountState> state = accounts.state(user.id());
            if (state.isEmpty() || !state.get().active()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                JsonProblemWriter.write(response, 401, "Não autenticado", "UNAUTHENTICATED",
                        "É preciso entrar para continuar.");
                return;
            }
            if (state.get().mustChangePassword()
                    && !ALLOWED_WHILE_PASSWORD_PENDING.contains(request.getMethod() + " " + request.getRequestURI())) {
                JsonProblemWriter.write(response, 403, "Troca de senha obrigatória", "PASSWORD_CHANGE_REQUIRED",
                        "Troque a senha provisória para continuar.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
