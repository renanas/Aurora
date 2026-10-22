package br.com.aurora.auth.web;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e logout são tratados por filtros do Spring Security (ver SecurityConfig).
 * Este endpoint só responde "quem sou eu": o frontend o usa ao abrir para saber se há sessão válida.
 * Sem sessão, o filtro de segurança já devolve 401 antes de chegar aqui.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public MeResponse me(Principal principal) {
        return new MeResponse(principal.getName());
    }
}
