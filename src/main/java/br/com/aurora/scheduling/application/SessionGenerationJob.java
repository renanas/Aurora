package br.com.aurora.scheduling.application;

import br.com.aurora.scheduling.persistence.RecurrenceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mantém o horizonte de sessões em dia: ao subir o sistema e uma vez por dia. */
@Component
public class SessionGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(SessionGenerationJob.class);

    private final RecurrenceRepository recurrences;
    private final SessionGenerator generator;
    private final Clock clock;

    public SessionGenerationJob(RecurrenceRepository recurrences, SessionGenerator generator, Clock clock) {
        this.recurrences = recurrences;
        this.generator = generator;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void daily() {
        run();
    }

    /** Uma transação por recorrência: se uma falhar, as outras continuam. */
    void run() {
        List<UUID> ids = recurrences.findGeneratingIds(LocalDate.now(clock));
        int created = 0;
        for (UUID id : ids) {
            try {
                created += generator.generateFor(id);
            } catch (RuntimeException e) {
                // Sem a mensagem da exceção: ela pode conter valores do banco.
                log.error("Falha ao gerar sessões: recurrenceId={} type={}", id, e.getClass().getName());
            }
        }
        log.info("Geração de sessões concluída: recorrências={} novasSessões={}", ids.size(), created);
    }
}
