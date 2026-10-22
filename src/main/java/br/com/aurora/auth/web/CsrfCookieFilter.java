package br.com.aurora.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * O Spring Security só gera o token CSRF quando alguém o lê. Como o frontend é uma SPA e nunca
 * renderiza o token, este filtro o lê em toda requisição para garantir que o cookie XSRF-TOKEN
 * seja enviado (inclusive na primeira resposta, que é um 401 de GET /api/auth/me).
 * Não é um bean: é criado dentro da SecurityConfig para não ser registrado duas vezes.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // força a gravação do cookie
        }
        chain.doFilter(request, response);
    }
}
