package br.com.aurora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Sem subir o Spring inteiro: controller de teste + handler real. */
class GlobalExceptionHandlerTest {

    private static final String PII = "Maria 5511999998888";

    private MockMvc mvc;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();

        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("recurso inexistente vira 404 NOT_FOUND")
    void notFound() throws Exception {
        mvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("regra de negócio violada vira 422 com o código da regra")
    void businessRule() throws Exception {
        mvc.perform(get("/probe/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("sobreposição vira 409 OVERLAP_DETECTED com os conflitos, horários sem segundos")
    void overlap() throws Exception {
        mvc.perform(get("/probe/overlap"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"))
                .andExpect(jsonPath("$.conflicts[0].patientName").value("Maria"))
                .andExpect(jsonPath("$.conflicts[0].startsAt").value("2026-09-30T09:30"))
                .andExpect(jsonPath("$.conflicts[0].endsAt").value("2026-09-30T10:20"));
    }

    @Test
    @DisplayName("edição concorrente vira 409 CONCURRENT_MODIFICATION")
    void concurrentModification() throws Exception {
        mvc.perform(get("/probe/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("validação vira 400 VALIDATION_ERROR com a lista de campos")
    void validation() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("JSON malformado vira 400 e o corpo enviado não é ecoado")
    void malformedJsonIsNotEchoed() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": SEGREDO-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString("SEGREDO-123"))));
    }

    @Test
    @DisplayName("UUID inválido na URL vira 400 e o valor recebido não é ecoado")
    void typeMismatchIsNotEchoed() throws Exception {
        mvc.perform(get("/probe/uuid/valor-estranho-777"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail", not(containsString("valor-estranho-777"))));
    }

    @Test
    @DisplayName("erro inesperado vira 500 genérico: mensagem da exceção não chega ao cliente")
    void unexpectedErrorIsGeneric() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("5511999998888"))))
                .andExpect(content().string(not(containsString("Maria"))));
    }

    @Test
    @DisplayName("erro inesperado não registra a mensagem da exceção nem stack trace com mensagem no log")
    void unexpectedErrorDoesNotLogPersonalData() throws Exception {
        mvc.perform(get("/probe/boom?search=Maria")).andExpect(status().isInternalServerError());

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("5511999998888").doesNotContain("Maria");
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("toda resposta tem requestId, igual ao cabeçalho X-Request-Id")
    void requestIdInHeaderAndBody() throws Exception {
        MvcResult result = mvc.perform(get("/probe/not-found"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(result.getResponse().getContentAsString()).contains(requestId);
    }

    @Test
    @DisplayName("o toString de Conflict não expõe o nome do paciente")
    void conflictToStringHidesName() {
        Conflict conflict = new Conflict(UUID.randomUUID(), "Maria",
                LocalDateTime.of(2026, 9, 30, 9, 30), LocalDateTime.of(2026, 9, 30, 10, 20));

        assertThat(conflict.toString()).doesNotContain("Maria");
    }

    // ---------- controller de teste ----------

    @RestController
    static class ProbeController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new NotFoundException("Paciente", UUID.randomUUID());
        }

        @GetMapping("/probe/rule")
        void rule() {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION", "Transição de status inválida.");
        }

        @GetMapping("/probe/overlap")
        void overlap() {
            throw new OverlapDetectedException(List.of(new Conflict(UUID.randomUUID(), "Maria",
                    LocalDateTime.of(2026, 9, 30, 9, 30), LocalDateTime.of(2026, 9, 30, 10, 20))));
        }

        @GetMapping("/probe/lock")
        void lock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
        }

        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        @GetMapping("/probe/uuid/{id}")
        void uuid(@PathVariable UUID id) {
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("falha com dados de " + PII);
        }
    }
}
