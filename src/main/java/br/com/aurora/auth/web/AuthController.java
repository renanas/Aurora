package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AuroraUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e logout são tratados por filtros do Spring Security (ver SecurityConfig).
 * Este endpoint só responde "quem sou eu": o frontend o usa ao abrir para saber se há sessão válida
 * e para mostrar o nome no cabeçalho. Sem sessão, o filtro de segurança já devolve 401 antes de
 * chegar aqui.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        AuroraUserDetails user = (AuroraUserDetails) authentication.getPrincipal();
        return new MeResponse(user.getUsername(), user.displayName(), user.roleName());
    }
}
