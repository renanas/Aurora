package br.com.aurora.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único relógio do sistema. Nenhum código deve chamar LocalDate.now() ou LocalDateTime.now():
 * injete o Clock. Nos testes, use Clock.fixed(...).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(SystemConstants.ZONE);
    }
}
