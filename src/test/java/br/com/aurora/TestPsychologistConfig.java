package br.com.aurora;

import br.com.aurora.auth.persistence.AppUserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Nos testes de API sem Spring Security, "quem está logada" vem daqui, no lugar da implementação do
 * módulo auth. Quem precisa de login de verdade (AuthApiTest) não importa esta classe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPsychologistConfig {

    @Bean
    @Primary
    TestPsychologists testPsychologists(AppUserRepository users) {
        return new TestPsychologists(users);
    }
}
