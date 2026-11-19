package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.PasswordPolicy;
import br.com.aurora.auth.persistence.AppUserRepository;
import br.com.aurora.shared.error.BusinessRuleException;
import br.com.aurora.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas de usuárias: cadastro pela própria pessoa, aprovado pelo administrador; troca de senha pela
 * própria pessoa e a conferência de estado a cada requisição.
 * Senhas e e-mails nunca vão para log nem para mensagem de erro.
 */
@Service
public class AccountService {

    /** Teto de cadastros aguardando aprovação (a rota de cadastro é pública). */
    static final int MAX_PENDING_REGISTRATIONS = 20;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccountDetails> list() {
        return users.findAllByOrderByCreatedAtAsc().stream().map(AccountDetails::from).toList();
    }

    /**
     * Cadastro pela própria pessoa (rota pública): a conta nasce psicóloga e aguardando aprovação.
     * Se o e-mail já tem conta, nada acontece e a resposta é a mesma (a tela pública não revela quem já
     * está cadastrada). O hash é calculado sempre, para o tempo de resposta também não revelar.
     * Há um teto de cadastros pendentes, para a rota pública não encher o banco.
     */
    @Transactional
    public void register(String displayName, String email, String password) {
        PasswordPolicy.validate(password);
        AppUser user;
        try {
            user = AppUser.register(email, encoder.encode(password), displayName, Instant.now(clock));
        } catch (IllegalArgumentException invalid) {
            // As mensagens do domínio são constantes ("E-mail inválido.", "Nome inválido."), sem o valor digitado.
            throw new BusinessRuleException("INVALID_ACCOUNT_DATA", invalid.getMessage());
        }
        if (users.countByApprovedFalse() >= MAX_PENDING_REGISTRATIONS) {
            throw new BusinessRuleException("TOO_MANY_PENDING_REGISTRATIONS",
                    "Há muitos cadastros aguardando aprovação. Tente de novo mais tarde.");
        }
        if (users.existsByEmail(user.email())) {
            return;
        }
        users.save(user);
    }

    /** Aprova um cadastro pendente. Aprovar uma conta já aprovada não muda nada. */
    @Transactional
    public AccountDetails approve(UUID targetId) {
        AppUser target = users.findById(targetId).orElseThrow(() -> new NotFoundException("Conta", targetId));
        target.approve();
        return AccountDetails.from(target);
    }

    /** Recusa um cadastro pendente: a conta é apagada (ela nunca entrou, então não tem dados). */
    @Transactional
    public void reject(UUID targetId) {
        AppUser target = users.findById(targetId).orElseThrow(() -> new NotFoundException("Conta", targetId));
        if (target.isApproved()) {
            throw new BusinessRuleException("ACCOUNT_NOT_PENDING",
                    "Esta conta já foi aprovada. Para tirar o acesso, desative-a.");
        }
        users.delete(target);
    }

    @Transactional
    public AccountDetails setActive(UUID actingId, UUID targetId, boolean active) {
        AppUser target = loadOther(actingId, targetId);
        if (active) {
            target.activate();
        } else {
            target.deactivate();
        }
        return AccountDetails.from(target);
    }

    @Transactional
    public AccountDetails resetPassword(UUID actingId, UUID targetId, String temporaryPassword) {
        AppUser target = loadOther(actingId, targetId);
        PasswordPolicy.validate(temporaryPassword);
        target.resetPassword(encoder.encode(temporaryPassword));
        return AccountDetails.from(target);
    }

    @Transactional
    public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("Conta", id));
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessRuleException("WRONG_CURRENT_PASSWORD", "A senha atual está incorreta.");
        }
        PasswordPolicy.validate(newPassword);
        if (currentPassword.equals(newPassword)) {
            throw new BusinessRuleException("SAME_PASSWORD", "A nova senha deve ser diferente da atual.");
        }
        user.changePassword(encoder.encode(newPassword));
    }

    /** Estado atual da conta; vazio se ela não existe mais. Consultado a cada requisição de quem está logada. */
    @Transactional(readOnly = true)
    public Optional<AccountState> state(UUID id) {
        return users.findById(id).map(user -> new AccountState(user.canLogIn(), user.mustChangePassword()));
    }

    /** Conta de outra pessoa: a administradora não desativa nem redefine a própria (para não se trancar para fora). */
    private AppUser loadOther(UUID actingId, UUID targetId) {
        if (actingId.equals(targetId)) {
            throw new BusinessRuleException("CANNOT_MODIFY_OWN_ACCOUNT",
                    "Você não pode desativar nem redefinir a senha da sua própria conta por aqui.");
        }
        return users.findById(targetId).orElseThrow(() -> new NotFoundException("Conta", targetId));
    }
}
