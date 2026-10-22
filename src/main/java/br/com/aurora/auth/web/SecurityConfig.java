package br.com.aurora.auth.web;

import br.com.aurora.auth.application.LoginAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Autenticação do Aurora: usuária única, sessão por cookie, tudo no mesmo domínio (sem CORS).
 *
 *  - Todo /api/** exige login; só /actuator/health é público. O que não é /api nem health é negado
 *    (nada sai sem sessão; o deploy decide depois como servir o frontend).
 *  - Login: POST /api/auth/login com formulário (username, password) -> 204. Logout: POST
 *    /api/auth/logout -> 204. Erros no formato Problem Details com "code".
 *  - CSRF: cookie XSRF-TOKEN legível pelo JavaScript, devolvido no cabeçalho X-XSRF-TOKEN.
 *  - Cookie de sessão (JSESSIONID): HttpOnly, SameSite=Strict, Secure em produção (application.yml).
 *  - A usuária vem de aurora.auth.username / aurora.auth.password (variáveis de ambiente
 *    AURORA_AUTH_USERNAME / AURORA_AUTH_PASSWORD). Sem elas, ninguém consegue entrar.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_LOGIN_FAILURES = 5;
    static final Duration LOGIN_LOCK = Duration.ofMinutes(15);

    @Bean
    LoginAttemptLimiter loginAttemptLimiter() {
        return new LoginAttemptLimiter(Clock.systemUTC(), MAX_LOGIN_FAILURES, LOGIN_LOCK);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Único usuário, em memória. A senha das variáveis de ambiente é transformada em hash (BCrypt)
     * na subida. Sem usuário configurado a aplicação sobe, mas ninguém loga (falha fechada).
     */
    @Bean
    UserDetailsService userDetailsService(
            @Value("${aurora.auth.username:}") String username,
            @Value("${aurora.auth.password:}") String password,
            PasswordEncoder encoder) {
        if (username.isBlank() || password.isBlank()) {
            log.warn("Usuário de login não configurado (AURORA_AUTH_USERNAME / AURORA_AUTH_PASSWORD): "
                    + "ninguém conseguirá entrar.");
            return new InMemoryUserDetailsManager();
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "aurora.auth.password precisa ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("USER").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginAttemptLimiter limiter,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {

        // Mesmos atributos do cookie de sessão: SameSite=Strict e Secure conforme o ambiente.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(secureCookies));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // O token vai por cookie e cabeçalho, nunca dentro de uma resposta
                        // comprimida (cenário do BREACH): o tratador simples basta.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(new LoginThrottleFilter(limiter), UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login") // rota do frontend; impede a página de login padrão do Spring
                        .loginProcessingUrl(LoginThrottleFilter.LOGIN_PATH)
                        .successHandler((request, response, authentication) -> {
                            limiter.recordSuccess();
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        })
                        .failureHandler((request, response, exception) -> {
                            limiter.recordFailure();
                            JsonProblemWriter.write(response, 401, "Credenciais inválidas",
                                    "BAD_CREDENTIALS", "Usuário ou senha incorretos.");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                JsonProblemWriter.write(response, 401, "Não autenticado",
                                        "UNAUTHENTICATED", "É preciso entrar para continuar."))
                        .accessDeniedHandler((request, response, exception) ->
                                JsonProblemWriter.write(response, 403, "Acesso negado", "FORBIDDEN",
                                        "A página ficou desatualizada. Atualize e tente de novo.")))
                // Sem sessão só para "lembrar a URL pedida": a SPA não precisa disso.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()));

        return http.build();
    }
}
