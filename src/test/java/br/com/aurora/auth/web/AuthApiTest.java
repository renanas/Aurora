package br.com.aurora.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.auth.application.AdminBootstrap;
import br.com.aurora.auth.application.LoginAttemptLimiter;
import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.domain.Role;
import br.com.aurora.auth.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
 * Login, sessão, CSRF, contas do banco e limite de tentativas, contra a cadeia de segurança real.
 * Os outros testes de API montam o MockMvc sem o Spring Security (de propósito: testam as regras
 * de negócio, não o login); aqui o filtro de segurança entra via springSecurity().
 * A conta do administrador é criada na subida do contexto pelo AdminBootstrap (propriedades abaixo).
 */
@SpringBootTest(properties = {
        "aurora.auth.username=psicologa@aurora.test",
        "aurora.auth.password=senha-de-teste-123",
        "aurora.auth.display-name=Dra. Teste"
})
@Import(TestcontainersConfig.class)
class AuthApiTest {

    private static final String USER = "psicologa@aurora.test";
    private static final String PASSWORD = "senha-de-teste-123";
    private static final String WRONG = "senha-errada-123";

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
    final List<AppUser> extraUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        limiter.reset(); // o limitador é um bean compartilhado entre os testes: reinicia
    }

    @AfterEach
    void removeExtraUsers() {
        // Pacientes e sessões apontam para as contas: sai tudo antes das contas extras.
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
        users.deleteAll(extraUsers); // só as contas criadas pelo teste; a do administrador fica
        extraUsers.clear();
    }

    // ---------- helpers ----------

    private AppUser createUser(String email, String password, Role role, boolean active) {
        AppUser user = AppUser.create(email, encoder.encode(password), "Conta de teste", role, false, Instant.now());
        if (!active) {
            user.deactivate();
        }
        AppUser saved = users.save(user);
        extraUsers.add(saved);
        return saved;
    }

    /** Pede qualquer rota e lê o token CSRF que o servidor entrega no cookie XSRF-TOKEN. */
    private String csrfToken() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me")).andReturn();
        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        String prefix = "XSRF-TOKEN=";
        return cookies.stream()
                .filter(c -> c.startsWith(prefix))
                .map(c -> {
                    int end = c.indexOf(';');
                    return c.substring(prefix.length(), end < 0 ? c.length() : end);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("o servidor não enviou o cookie XSRF-TOKEN"));
    }

    private ResultActions login(String username, String password, String token) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", username)
                .param("password", password)
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token));
    }

    private MockHttpSession loggedInSession(String token) throws Exception {
        MvcResult result = login(USER, PASSWORD, token).andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ---------- sem login ----------

    @Test
    @DisplayName("sem login, a API responde 401 com código estável")
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a primeira resposta já entrega o cookie do token CSRF")
    void csrfCookieIsIssued() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("XSRF-TOKEN="));
    }

    @Test
    @DisplayName("o health check continua público")
    void healthIsPublic() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    // ---------- login e logout ----------

    @Test
    @DisplayName("login correto abre a sessão e /me responde com e-mail, nome e perfil")
    void loginOpensSession() throws Exception {
        MockHttpSession session = loggedInSession(csrfToken());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USER))
                .andExpect(jsonPath("$.displayName").value("Dra. Teste"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/patients").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("o e-mail do login ignora maiúsculas e espaços nas pontas")
    void loginIgnoresCaseAndSpaces() throws Exception {
        String token = csrfToken();

        MvcResult result = login("  Psicologa@AURORA.test ", PASSWORD, token)
                .andExpect(status().isNoContent()).andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.username").value(USER));
    }

    @Test
    @DisplayName("uma segunda conta entra e /me mostra o nome e o perfil dela")
    void secondAccountLogsIn() throws Exception {
        createUser("bia@aurora.test", "senha-da-bia-123", Role.PSYCHOLOGIST, true);
        String token = csrfToken();

        MvcResult result = login("bia@aurora.test", "senha-da-bia-123", token)
                .andExpect(status().isNoContent()).andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.username").value("bia@aurora.test"))
                .andExpect(jsonPath("$.displayName").value("Conta de teste"))
                .andExpect(jsonPath("$.role").value("PSYCHOLOGIST"));
    }

    @Test
    @DisplayName("senha errada: 401 BAD_CREDENTIALS e nenhuma sessão")
    void wrongPassword() throws Exception {
        login(USER, WRONG, csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("e-mail inexistente recebe a mesma resposta da senha errada")
    void unknownUser() throws Exception {
        login("outra-pessoa@aurora.test", PASSWORD, csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("conta desativada não entra, nem com a senha certa, e a resposta é a mesma")
    void inactiveAccountCannotLogIn() throws Exception {
        createUser("inativa@aurora.test", "senha-inativa-123", Role.PSYCHOLOGIST, false);

        login("inativa@aurora.test", "senha-inativa-123", csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("login sem token CSRF é recusado")
    void loginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", USER)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sessão válida, mas sem token CSRF, não consegue alterar dados")
    void mutationWithoutCsrfIsRejected() throws Exception {
        MockHttpSession session = loggedInSession(csrfToken());

        mvc.perform(post("/api/patients").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("logout encerra a sessão")
    void logoutEndsSession() throws Exception {
        String token = csrfToken();
        MockHttpSession session = loggedInSession(token);

        mvc.perform(post("/api/auth/logout").session(session)
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    // ---------- limite de tentativas ----------

    @Test
    @DisplayName("depois de 5 falhas seguidas o e-mail é bloqueado, até com a senha certa")
    void lockoutAfterRepeatedFailures() throws Exception {
        String token = csrfToken();
        for (int i = 0; i < 5; i++) {
            login(USER, WRONG, token).andExpect(status().isUnauthorized());
        }

        login(USER, PASSWORD, token)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("o bloqueio de uma conta não impede as outras de entrar")
    void lockoutIsPerEmail() throws Exception {
        createUser("bia@aurora.test", "senha-da-bia-123", Role.PSYCHOLOGIST, true);
        String token = csrfToken();
        for (int i = 0; i < 5; i++) {
            login(USER, WRONG, token).andExpect(status().isUnauthorized());
        }
        login(USER, PASSWORD, token).andExpect(status().isTooManyRequests());

        login("bia@aurora.test", "senha-da-bia-123", token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("e-mail que não existe também é bloqueado, igual a um que existe")
    void unknownEmailIsLockedLikeARealOne() throws Exception {
        String token = csrfToken();
        for (int i = 0; i < 5; i++) {
            login("ninguem@aurora.test", WRONG, token).andExpect(status().isUnauthorized());
        }

        login("ninguem@aurora.test", WRONG, token)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("as falhas contam juntas mesmo com maiúsculas e espaços diferentes no e-mail")
    void failuresCountAcrossSpellings() throws Exception {
        String token = csrfToken();
        String[] spellings = {USER, USER.toUpperCase(), " " + USER, USER + " ", "Psicologa@Aurora.Test"};
        for (String spelling : spellings) {
            login(spelling, WRONG, token).andExpect(status().isUnauthorized());
        }

        login(USER, PASSWORD, token).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("login correto zera a contagem de falhas")
    void successResetsFailureCount() throws Exception {
        String token = csrfToken();
        for (int i = 0; i < 4; i++) {
            login(USER, WRONG, token).andExpect(status().isUnauthorized());
        }
        login(USER, PASSWORD, token).andExpect(status().isNoContent());

        for (int i = 0; i < 4; i++) {
            login(USER, WRONG, token).andExpect(status().isUnauthorized());
        }
        login(USER, PASSWORD, token).andExpect(status().isNoContent());
    }

    // ---------- dona dos dados (CurrentPsychologist de verdade, com login) ----------

    private MockHttpSession loginSession(String email, String password, String token) throws Exception {
        MvcResult result = login(email, password, token).andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("o paciente criado por quem está logada é dela, e outra conta não o vê nem o acessa")
    void patientBelongsToTheLoggedInAccount() throws Exception {
        createUser("bia@aurora.test", "senha-da-bia-123", Role.PSYCHOLOGIST, true);
        String token = csrfToken();
        MockHttpSession admin = loggedInSession(token);

        String body = mvc.perform(post("/api/patients").session(admin)
                        .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria\",\"whatsapp\":\"5511999998888\",\"sessionPrice\":180}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String patientId = JsonPath.read(body, "$.id");

        UUID owner = jdbc.queryForObject("SELECT psychologist_id FROM patient WHERE id = ?", UUID.class,
                UUID.fromString(patientId));
        assertThat(owner).isEqualTo(users.findByEmail(USER).orElseThrow().id());

        MockHttpSession bia = loginSession("bia@aurora.test", "senha-da-bia-123", token);
        mvc.perform(get("/api/patients").session(bia))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/patients/" + patientId).session(bia))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/patients/" + patientId).session(admin))
                .andExpect(status().isOk());
    }

    // ---------- conta inicial (AdminBootstrap) ----------

    @Test
    @DisplayName("a conta inicial foi criada na subida, como administrador")
    void bootstrapCreatedTheAdmin() {
        AppUser admin = users.findByEmail(USER).orElseThrow();

        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.passwordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, admin.passwordHash())).isTrue();
    }

    @Test
    @DisplayName("rodar o bootstrap de novo, mesmo com outra senha na variável, não muda a conta existente")
    void bootstrapNeverOverwritesAnExistingAccount() throws Exception {
        long before = users.count();
        String hashBefore = users.findByEmail(USER).orElseThrow().passwordHash();

        new AdminBootstrap(users, encoder, " Psicologa@Aurora.test ", "outra-senha-longa-123", "Outro Nome")
                .ensureAdmin();

        assertThat(users.count()).isEqualTo(before);
        assertThat(users.findByEmail(USER).orElseThrow().passwordHash()).isEqualTo(hashBefore);
        login(USER, PASSWORD, csrfToken()).andExpect(status().isNoContent());
        login(USER, "outra-senha-longa-123", csrfToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("senha curta na variável impede a subida")
    void bootstrapRejectsShortPassword() {
        AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, "nova@aurora.test", "curta", "Admin");

        assertThatThrownBy(bootstrap::ensureAdmin).isInstanceOf(IllegalStateException.class);
        assertThat(users.existsByEmail("nova@aurora.test")).isFalse();
    }

    @Test
    @DisplayName("sem as variáveis nada é criado e nada quebra")
    void bootstrapWithoutVariablesDoesNothing() {
        long before = users.count();

        new AdminBootstrap(users, encoder, "", "", "Admin").ensureAdmin();

        assertThat(users.count()).isEqualTo(before);
    }
}
