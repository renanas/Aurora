package br.com.aurora.auth.application;

import br.com.aurora.shared.security.CurrentPsychologist;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Lê a conta logada do contexto de segurança. Sem conta autenticada (ou anônima), falha em vez de
 * devolver um dono qualquer: na dúvida, nada é lido nem gravado.
 */
@Component
public class AuthenticatedPsychologist implements CurrentPsychologist {

    @Override
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuroraUserDetails user) {
            return user.id();
        }
        throw new IllegalStateException("Nenhuma conta autenticada nesta requisição.");
    }
}
