package br.com.aurora.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.auth.application.LoginAttemptLimiter;
import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.Role;
import br.com.aurora.auth.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Cadastro público com aprovação, administração de contas, troca de senha e a reconferência da conta a cada requisição, contra a cadeia de
 * segurança real. Usa as mesmas propriedades do AuthApiTest (assim as duas classes dividem o mesmo contexto e o
 * mesmo banco): o administrador é a conta inicial criada pelo AdminBootstrap.
 */
@SpringBootTest(properties = {
        "aurora.auth.username=psicologa@aurora.test",
        "aurora.auth.password=senha-de-teste-123",
        "aurora.auth.display-name=Dra. Teste"
})
@Import(TestcontainersConfig.class)
class AccountsApiTest {

    private static final String ADMIN = "psicologa@aurora.test";
    private static final String ADMIN_PASSWORD = "senha-de-teste-123";
    private static final String TEMP = "provisoria-1234";
    private static final String NEW_PASSWORD = "senha-nova-5678";

    @Autowired
    WebApplicationContext context;
    @Autowired
    LoginAttemptLimiter limiter;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        limiter.reset();
    }

    /** O banco é compartilhado com o AuthApiTest: some tudo o que estes testes criaram (menos o administrador). */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
        jdbc.update("DELETE FROM app_user WHERE email <> ?", ADMIN);
    }

    // ---------- helpers ----------

    private String csrfToken() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me")).andReturn();
        String prefix = "XSRF-TOKEN=";
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(cookieHeader -> cookieHeader.startsWith(prefix))
                .map(cookieHeader -> cookieHeader.substring(prefix.length(), cookieHeader.indexOf(';') < 0 ? cookieHeader.length() : cookieHeader.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("o servidor não enviou o cookie XSRF-TOKEN"));
    }

    private ResultActions login(String email, String password, String token) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", email)
                .param("password", password)
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token));
    }

    private MockHttpSession sessionFor(String email, String password, String token) throws Exception {
        MvcResult result = login(email, password, token).andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions postJson(MockHttpSession session, String token, String url, String json) throws Exception {
        return mvc.perform(post(url).session(session)
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Cadastro público: sem sessão, mas com o token CSRF. */
    private ResultActions register(String token, String json) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String registerJson(String name, String email, String password) {
        return "{\"displayName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private ResultActions putJson(MockHttpSession session, String token, String url, String json) throws Exception {
        return mvc.perform(put(url).session(session)
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String emailJson(String email, String currentPassword) {
        return "{\"email\":\"" + email + "\",\"currentPassword\":\"" + currentPassword + "\"}";
    }

    private static String passwordJson(String password) {
        return "{\"temporaryPassword\":\"" + password + "\"}";
    }

    private static String changeJson(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private UUID createPsychologist(String email, String password, boolean mustChangePassword) {
        return users.save(AppUser.create(email, encoder.encode(password), "Conta de teste", Role.PSYCHOLOGIST,
                mustChangePassword, Instant.now())).id();
    }

    // ---------- quem pode administrar ----------

    @Test
    @DisplayName("sem login, as rotas de administração respondem 401")
    void adminEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/admin/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("psicóloga não administra contas: 403 NOT_ALLOWED e nada é aprovado")
    void psychologistCannotAdministerAccounts() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        register(token, registerJson("Bia", "bia@aurora.test", NEW_PASSWORD)).andExpect(status().isAccepted());
        UUID biaId = users.findByEmail("bia@aurora.test").orElseThrow().id();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);

        mvc.perform(get("/api/admin/accounts").session(ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED"));
        postJson(ana, token, "/api/admin/accounts/" + biaId + "/approve", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED"));

        assertThat(users.findById(biaId).orElseThrow().isApproved()).isFalse();
    }

    @Test
    @DisplayName("a rota de administração continua exigindo o token CSRF")
    void adminPostStillRequiresCsrf() throws Exception {
        UUID anaId = createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        mvc.perform(post("/api/admin/accounts/" + anaId + "/deactivate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a administradora não cria mais contas diretamente")
    void adminNoLongerCreatesAccounts() throws Exception {
        String token = csrfToken();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);
        long before = users.count();

        postJson(admin, token, "/api/admin/accounts", registerJson("Nova", "nova@aurora.test", TEMP))
                .andExpect(status().is4xxClientError());

        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("a administradora lista as contas, sem senha nem hash")
    void adminListsAccountsWithoutSecrets() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        String body = mvc.perform(get("/api/admin/accounts").session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> emails = JsonPath.read(body, "$[*].email");
        assertThat(emails).contains(ADMIN, "ana@aurora.test");
        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("senha-da-ana-123");
    }

    // ---------- cadastro pela própria pessoa e aprovação ----------

    @Test
    @DisplayName("cadastro público: a conta nasce aguardando aprovação e não entra")
    void registeredAccountWaitsForApproval() throws Exception {
        String token = csrfToken();

        register(token, registerJson("Dra. Bia", " Bia@Aurora.Test ", NEW_PASSWORD))
                .andExpect(status().isAccepted());

        AppUser bia = users.findByEmail("bia@aurora.test").orElseThrow();
        assertThat(bia.isApproved()).isFalse();
        assertThat(bia.role()).isEqualTo(Role.PSYCHOLOGIST);
        assertThat(bia.mustChangePassword()).isFalse();
        login("bia@aurora.test", NEW_PASSWORD, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);
        String list = mvc.perform(get("/api/admin/accounts").session(admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Boolean> approved = JsonPath.read(list, "$[?(@.email == 'bia@aurora.test')].approved");
        assertThat(approved).containsExactly(false);
    }

    @Test
    @DisplayName("depois de aprovada, entra com a senha que escolheu e usa o sistema sem trocar a senha")
    void approvedAccountLogsIn() throws Exception {
        String token = csrfToken();
        register(token, registerJson("Bia", "bia@aurora.test", NEW_PASSWORD)).andExpect(status().isAccepted());
        UUID biaId = users.findByEmail("bia@aurora.test").orElseThrow().id();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        postJson(admin, token, "/api/admin/accounts/" + biaId + "/approve", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.active").value(true));
        postJson(admin, token, "/api/admin/accounts/" + biaId + "/approve", "{}") // de novo: nada muda
                .andExpect(status().isOk());

        MockHttpSession bia = sessionFor("bia@aurora.test", NEW_PASSWORD, token);
        mvc.perform(get("/api/auth/me").session(bia))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/patients").session(bia)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("recusar apaga o cadastro pendente; conta já aprovada não pode ser recusada")
    void rejectDeletesPendingRegistration() throws Exception {
        UUID anaId = createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        register(token, registerJson("Bia", "bia@aurora.test", NEW_PASSWORD)).andExpect(status().isAccepted());
        UUID biaId = users.findByEmail("bia@aurora.test").orElseThrow().id();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        postJson(admin, token, "/api/admin/accounts/" + biaId + "/reject", "{}")
                .andExpect(status().isNoContent());
        assertThat(users.findById(biaId)).isEmpty();

        postJson(admin, token, "/api/admin/accounts/" + anaId + "/reject", "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_PENDING"));
        assertThat(users.findById(anaId)).isPresent();
    }

    @Test
    @DisplayName("e-mail já cadastrado: mesma resposta 202 e nada muda na conta existente")
    void duplicateRegistrationIsSilent() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        long before = users.count();

        register(token, registerJson("Outra Ana", " ANA@Aurora.Test ", NEW_PASSWORD))
                .andExpect(status().isAccepted());

        assertThat(users.count()).isEqualTo(before);
        login("ana@aurora.test", "senha-da-ana-123", token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("dados inválidos no cadastro são recusados e nada é criado")
    void registerValidatesTheData() throws Exception {
        String token = csrfToken();
        long before = users.count();

        register(token, registerJson("Bia", "bia@aurora.test", "curta"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        register(token, registerJson("Bia", "bia@aurora.test", "a".repeat(73)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        register(token, registerJson("Bia", "sem-arroba", NEW_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_DATA"));
        register(token, registerJson("   ", "bia@aurora.test", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("o cadastro público exige o token CSRF")
    void registerRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Bia", "bia@aurora.test", NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(users.findByEmail("bia@aurora.test")).isEmpty();
    }

    @Test
    @DisplayName("há um teto de cadastros aguardando aprovação")
    void pendingRegistrationsAreCapped() throws Exception {
        for (int i = 0; i < 20; i++) {
            users.save(AppUser.register("pendente" + i + "@aurora.test", "hash", "Pendente", Instant.now()));
        }
        String token = csrfToken();

        register(token, registerJson("Bia", "bia@aurora.test", NEW_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PENDING_REGISTRATIONS"));
        assertThat(users.findByEmail("bia@aurora.test")).isEmpty();
    }

    // ---------- trocar a própria senha ----------

    @Test
    @DisplayName("a pessoa troca a senha provisória e passa a usar o sistema normalmente")
    void userChangesTheTemporaryPassword() throws Exception {
        createPsychologist("ana@aurora.test", TEMP, true);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", TEMP, token);

        postJson(ana, token, "/api/auth/password", changeJson("errada-errada-1", NEW_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
        postJson(ana, token, "/api/auth/password", changeJson(TEMP, TEMP))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
        postJson(ana, token, "/api/auth/password", changeJson(TEMP, "curta"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        postJson(ana, token, "/api/auth/password", changeJson(TEMP, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/patients").session(ana)).andExpect(status().isForbidden()); // nada mudou ainda

        postJson(ana, token, "/api/auth/password", changeJson(TEMP, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/patients").session(ana)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(ana))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        login("ana@aurora.test", TEMP, token).andExpect(status().isUnauthorized());
        login("ana@aurora.test", NEW_PASSWORD, token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("com a senha provisória pendente, ainda dá para sair")
    void canLogOutWhileThePasswordChangeIsPending() throws Exception {
        createPsychologist("ana@aurora.test", TEMP, true);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", TEMP, token);

        postJson(ana, token, "/api/auth/logout", "{}").andExpect(status().isNoContent());
    }

    // ---------- trocar o próprio nome e e-mail ----------

    @Test
    @DisplayName("a pessoa troca o próprio nome e o /me já devolve o nome novo")
    void userChangesOwnName() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);

        putJson(ana, token, "/api/auth/profile", "{\"displayName\":\"  Dra. Ana Souza  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dra. Ana Souza"));
        mvc.perform(get("/api/auth/me").session(ana))
                .andExpect(jsonPath("$.displayName").value("Dra. Ana Souza"));

        putJson(ana, token, "/api/auth/profile", "{\"displayName\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a pessoa troca o próprio e-mail com a senha atual e passa a entrar com o novo")
    void userChangesOwnEmail() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);

        putJson(ana, token, "/api/auth/email", emailJson(" Ana.Nova@Aurora.Test ", "senha-da-ana-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.nova@aurora.test"));

        mvc.perform(get("/api/auth/me").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.nova@aurora.test"));
        login("ana@aurora.test", "senha-da-ana-123", token).andExpect(status().isUnauthorized());
        login("ana.nova@aurora.test", "senha-da-ana-123", token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("trocar o e-mail: senha errada, e-mail de outra conta ou inválido são recusados")
    void changeEmailIsValidated() throws Exception {
        createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        createPsychologist("bia@aurora.test", "senha-da-bia-123", false);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);

        putJson(ana, token, "/api/auth/email", emailJson("ana2@aurora.test", "senha-errada-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
        putJson(ana, token, "/api/auth/email", emailJson(" BIA@aurora.test", "senha-da-ana-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        putJson(ana, token, "/api/auth/email", emailJson("sem-arroba", "senha-da-ana-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_DATA"));

        assertThat(users.findByEmail("ana@aurora.test")).isPresent();
    }

    // ---------- desativar, ativar e redefinir ----------

    @Test
    @DisplayName("conta desativada perde a sessão na hora e não entra mais; ativar devolve o acesso")
    void deactivatedAccountLosesTheSessionImmediately() throws Exception {
        UUID anaId = createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);
        mvc.perform(get("/api/auth/me").session(ana)).andExpect(status().isOk());

        postJson(admin, token, "/api/admin/accounts/" + anaId + "/deactivate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/auth/me").session(ana))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        login("ana@aurora.test", "senha-da-ana-123", token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        postJson(admin, token, "/api/admin/accounts/" + anaId + "/activate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        login("ana@aurora.test", "senha-da-ana-123", token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("redefinir a senha obriga a trocar, inclusive em quem já está logada")
    void resetPasswordForcesTheChange() throws Exception {
        UUID anaId = createPsychologist("ana@aurora.test", "senha-da-ana-123", false);
        String token = csrfToken();
        MockHttpSession ana = sessionFor("ana@aurora.test", "senha-da-ana-123", token);
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);
        mvc.perform(get("/api/patients").session(ana)).andExpect(status().isOk());

        postJson(admin, token, "/api/admin/accounts/" + anaId + "/reset-password", passwordJson("curta"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        postJson(admin, token, "/api/admin/accounts/" + anaId + "/reset-password", passwordJson(TEMP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(get("/api/patients").session(ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").session(ana))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        login("ana@aurora.test", "senha-da-ana-123", token).andExpect(status().isUnauthorized());
        login("ana@aurora.test", TEMP, token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a administradora não desativa nem redefine a própria conta")
    void adminCannotModifyOwnAccount() throws Exception {
        UUID adminId = users.findByEmail(ADMIN).orElseThrow().id();
        String token = csrfToken();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        for (String action : List.of("deactivate", "activate")) {
            postJson(admin, token, "/api/admin/accounts/" + adminId + "/" + action, "{}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_OWN_ACCOUNT"));
        }
        postJson(admin, token, "/api/admin/accounts/" + adminId + "/reset-password", passwordJson(TEMP))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_OWN_ACCOUNT"));

        assertThat(users.findByEmail(ADMIN).orElseThrow().isActive()).isTrue();
        login(ADMIN, ADMIN_PASSWORD, token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("conta que não existe responde 404")
    void unknownAccountIsNotFound() throws Exception {
        String token = csrfToken();
        MockHttpSession admin = sessionFor(ADMIN, ADMIN_PASSWORD, token);

        postJson(admin, token, "/api/admin/accounts/" + UUID.randomUUID() + "/deactivate", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
