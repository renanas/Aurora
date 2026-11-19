package br.com.aurora.auth.web;

import br.com.aurora.auth.application.AccountService;
import br.com.aurora.auth.application.AuroraUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administração de contas: aprovar ou recusar cadastros, desativar/reativar e redefinir senha. Só o perfil ADMIN chega aqui (regra no SecurityConfig, antes do controller).
 * Quem administra contas não ganha acesso a pacientes nem sessões de ninguém.
 */
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accounts.list().stream().map(AccountResponse::from).toList();
    }

    /** Aprova um cadastro feito pela própria pessoa. */
    @PostMapping("/{id}/approve")
    public AccountResponse approve(@PathVariable UUID id) {
        return AccountResponse.from(accounts.approve(id));
    }

    /** Recusa (apaga) um cadastro que ainda aguarda aprovação. */
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id) {
        accounts.reject(id);
    }

    @PostMapping("/{id}/deactivate")
    public AccountResponse deactivate(@PathVariable UUID id, Authentication authentication) {
        return AccountResponse.from(accounts.setActive(actingId(authentication), id, false));
    }

    @PostMapping("/{id}/activate")
    public AccountResponse activate(@PathVariable UUID id, Authentication authentication) {
        return AccountResponse.from(accounts.setActive(actingId(authentication), id, true));
    }

    @PostMapping("/{id}/reset-password")
    public AccountResponse resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request,
            Authentication authentication) {
        return AccountResponse.from(accounts.resetPassword(actingId(authentication), id, request.temporaryPassword()));
    }

    private static UUID actingId(Authentication authentication) {
        return ((AuroraUserDetails) authentication.getPrincipal()).id();
    }
}
