package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AccountService;
import br.com.aurora.auth.application.AuroraUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e logout são tratados por filtros do Spring Security (ver SecurityConfig).
 * Aqui ficam "quem sou eu" (o frontend usa ao abrir para saber se há sessão válida, mostrar o nome e
 * saber se a senha provisória precisa ser trocada) e a troca da própria senha. Sem sessão, o filtro de
 * segurança já devolve 401 antes de chegar aqui (menos no cadastro, que é público).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Lido do banco a cada chamada: nome e e-mail podem ter mudado depois do login. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return MeResponse.from(accounts.ownAccount(loggedUserId(authentication)));
    }

    /** Troca o nome que aparece no cabeçalho. */
    @PutMapping("/profile")
    public MeResponse changeName(@Valid @RequestBody ChangeNameRequest request, Authentication authentication) {
        return MeResponse.from(accounts.changeOwnName(loggedUserId(authentication), request.displayName()));
    }

    /** Troca o e-mail (o login). Pede a senha atual. */
    @PutMapping("/email")
    public MeResponse changeEmail(@Valid @RequestBody ChangeEmailRequest request, Authentication authentication) {
        return MeResponse.from(accounts.changeOwnEmail(
                loggedUserId(authentication), request.email(), request.currentPassword()));
    }

    /**
     * Cadastro público. Responde 202 sempre que os dados são válidos, inclusive se o e-mail já tem conta
     * (para não revelar quem está cadastrada). A conta só entra depois que o administrador aprovar.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        accounts.register(request.displayName(), request.email(), request.password());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        accounts.changeOwnPassword(loggedUserId(authentication), request.currentPassword(), request.newPassword());
    }

    private static UUID loggedUserId(Authentication authentication) {
        return ((AuroraUserDetails) authentication.getPrincipal()).id();
    }
}
