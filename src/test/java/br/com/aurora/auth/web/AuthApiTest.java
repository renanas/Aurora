package br.com.aurora.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.auth.application.LoginAttemptLimiter;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Login, sessão, CSRF e limite de tentativas, contra a cadeia de segurança real.
 * Os outros testes de API montam o MockMvc sem o Spring Security (de propósito: testam as regras
 * de negócio, não o login); aqui o filtro de segurança entra via springSecurity().
 */
@SpringBootTest(properties = {
        "aurora.auth.username=psicologa",
        "aurora.auth.password=senha-de-teste-123"
})
@Import(TestcontainersConfig.class)
class AuthApiTest {

    private static final String USER = "psicologa";
    private static final String PASSWORD = "senha-de-teste-123";

    @Autowired
    WebApplicationContext context;
    @Autowired
    LoginAttemptLimiter limiter;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        limiter.recordSuccess(); // o limitador é um bean compartilhado entre os testes: reinicia
    }

    // ---------- helpers ----------

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
    @DisplayName("login correto abre a sessão e /me responde com o usuário")
    void loginOpensSession() throws Exception {
        MockHttpSession session = loggedInSession(csrfToken());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USER));
        mvc.perform(get("/api/patients").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada: 401 BAD_CREDENTIALS e nenhuma sessão")
    void wrongPassword() throws Exception {
        login(USER, "senha-errada-123", csrfToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("usuário inexistente recebe a mesma resposta da senha errada")
    void unknownUser() throws Exception {
        login("outra-pessoa", PASSWORD, csrfToken())
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
    @DisplayName("depois de 5 falhas seguidas o login é bloqueado, até com a senha certa")
    void lockoutAfterRepeatedFailures() throws Exception {
        String token = csrfToken();
        for (int i = 0; i < 5; i++) {
            login(USER, "senha-errada-123", token).andExpect(status().isUnauthorized());
        }

        login(USER, PASSWORD, token)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("login correto zera a contagem de falhas")
    void successResetsFailureCount() throws Exception {
        String token = csrfToken();
        for (int i = 0; i < 4; i++) {
            login(USER, "senha-errada-123", token).andExpect(status().isUnauthorized());
        }
        login(USER, PASSWORD, token).andExpect(status().isNoContent());

        for (int i = 0; i < 4; i++) {
            login(USER, "senha-errada-123", token).andExpect(status().isUnauthorized());
        }
        login(USER, PASSWORD, token).andExpect(status().isNoContent());
    }
}
