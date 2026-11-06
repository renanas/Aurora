package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.Role;
import br.com.aurora.auth.persistence.AppUserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Conta inicial: na subida, se AURORA_AUTH_USERNAME (e-mail) e AURORA_AUTH_PASSWORD estão definidas
 * e a conta ainda não existe, cria o administrador. Se a conta já existe, NÃO mexe em nada (nem na
 * senha: trocar a variável não troca a senha de quem já existe). Sem as variáveis o app sobe e
 * nenhuma conta é criada (falha fechada: com o banco vazio, ninguém entra). Senha com menos de
 * 12 caracteres impede a subida. Nunca loga e-mail nem senha.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final String DEFAULT_DISPLAY_NAME = "Administrador";

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrap(
            AppUserRepository users,
            PasswordEncoder encoder,
            @Value("${aurora.auth.username:}") String username,
            @Value("${aurora.auth.password:}") String password,
            @Value("${aurora.auth.display-name:" + DEFAULT_DISPLAY_NAME + "}") String displayName) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();
    }

    public void ensureAdmin() {
        if (username.isBlank() || password.isBlank()) {
            log.warn("Conta inicial não configurada (AURORA_AUTH_USERNAME / AURORA_AUTH_PASSWORD): "
                    + "nenhuma conta será criada. Com o banco vazio, ninguém conseguirá entrar.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "aurora.auth.password precisa ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        String email = AppUser.normalizeEmail(username);
        if (users.existsByEmail(email)) {
            return;
        }
        String name = displayName.isBlank() ? DEFAULT_DISPLAY_NAME : displayName;
        users.save(AppUser.create(email, encoder.encode(password), name, Role.ADMIN, false, Instant.now()));
        log.info("Conta inicial do administrador criada.");
    }
}
