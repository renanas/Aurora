package br.com.aurora;

import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.Role;
import br.com.aurora.auth.persistence.AppUserRepository;
import br.com.aurora.shared.security.CurrentPsychologist;
import java.time.Instant;
import java.util.UUID;

/**
 * A "psicóloga logada" dos testes de API que montam o MockMvc sem o Spring Security. Por padrão é uma
 * conta fixa (criada no banco ao subir o contexto); um teste de isolamento troca de conta com {@link #as}
 * e devolve com {@link #reset}. As contas são de verdade (linhas em app_user), porque pacientes,
 * recorrências e sessões apontam para elas por chave estrangeira. Não servem para login.
 */
public class TestPsychologists implements CurrentPsychologist {

    private static final String DEFAULT_EMAIL = "psicologa-padrao@aurora.test";

    private final AppUserRepository users;
    private final UUID defaultId;
    private volatile UUID current;

    TestPsychologists(AppUserRepository users) {
        this.users = users;
        this.defaultId = create(DEFAULT_EMAIL);
        this.current = defaultId;
    }

    @Override
    public UUID id() {
        return current;
    }

    /** Cria a conta (ou devolve a que já existe com esse e-mail) e devolve o id. */
    public final UUID create(String email) {
        return users.findByEmail(email)
                .orElseGet(() -> users.save(AppUser.create(
                        email, "hash-que-nao-serve-para-login", "Conta de teste", Role.PSYCHOLOGIST, false,
                        Instant.now())))
                .id();
    }

    /** A partir daqui, as requisições do teste são feitas por esta conta. */
    public void as(UUID psychologistId) {
        this.current = psychologistId;
    }

    /** Volta para a conta padrão. */
    public void reset() {
        this.current = defaultId;
    }
}
