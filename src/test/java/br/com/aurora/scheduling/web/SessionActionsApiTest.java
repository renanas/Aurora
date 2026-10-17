package br.com.aurora.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.shared.error.RequestIdFilter;
import br.com.aurora.shared.time.SystemConstants;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Ações da sessão (entrega C) contra PostgreSQL real: confirmar, realizada, falta, cancelar e pagamento. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SessionActionsApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        cleanDatabase();
        tomorrow = LocalDate.now(SystemConstants.ZONE).plusDays(1);
    }

    /** O banco é compartilhado entre as classes de teste: não deixar linhas para o próximo (ex.: SchemaConstraintsTest). */
    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
    }

    // ---------- helpers ----------

    private String createPatient(String name) throws Exception {
        String body = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"whatsapp\":\"5511999998888\",\"sessionPrice\":180}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    /** Cria o paciente com horário semanal amanhã e devolve o id da primeira sessão gerada. */
    private String sessionFor(String patientName, String time) throws Exception {
        String patientId = createPatient(patientName);
        mvc.perform(put("/api/patients/" + patientId + "/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchorDate\":\"" + tomorrow + "\",\"startTime\":\"" + time
                                + "\",\"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id::text FROM therapy_session WHERE patient_id = ? ORDER BY starts_at LIMIT 1",
                String.class, UUID.fromString(patientId));
    }

    private ResultActions action(String sessionId, String name) throws Exception {
        return mvc.perform(post("/api/sessions/" + sessionId + "/" + name));
    }

    private ResultActions cancel(String sessionId, String type) throws Exception {
        return mvc.perform(post("/api/sessions/" + sessionId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancellationType\":\"" + type + "\"}"));
    }

    private ResultActions payment(String sessionId, String statusValue) throws Exception {
        return mvc.perform(put("/api/sessions/" + sessionId + "/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private String paidAtInDatabase(String sessionId) {
        return jdbc.queryForObject("SELECT paid_at::text FROM therapy_session WHERE id = ?",
                String.class, UUID.fromString(sessionId));
    }

    // ---------- situação ----------

    @Test
    @DisplayName("confirmar: 200 com a sessão CONFIRMADA; repetir também devolve 200")
    void confirm() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        action(id, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.patientName").value("Maria Silva"));
        action(id, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("realizada: vale a partir de AGENDADA e continua cobrável")
    void complete() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        action(id, "complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.billable").value(true));
    }

    @Test
    @DisplayName("falta: situação FALTOU, continua cobrável")
    void noShow() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        action(id, "no-show")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.billable").value(true));
    }

    @Test
    @DisplayName("cancelar com antecedência: não cobra; em cima da hora: cobra")
    void cancel() throws Exception {
        String early = sessionFor("Maria Silva", "09:00");
        String late = sessionFor("João Souza", "11:00");

        cancel(early, "EARLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationType").value("EARLY"))
                .andExpect(jsonPath("$.billable").value(false));
        cancel(late, "LATE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationType").value("LATE"))
                .andExpect(jsonPath("$.billable").value(true));
    }

    @Test
    @DisplayName("cancelar sem tipo ou com tipo desconhecido: 400 VALIDATION_ERROR, e a sessão não muda")
    void cancelRequiresAValidType() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        mvc.perform(post("/api/sessions/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        cancel(id, "TARDE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject("SELECT status FROM therapy_session WHERE id = ?", String.class,
                UUID.fromString(id))).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("ação que não cabe na situação atual: 422 INVALID_STATUS_TRANSITION")
    void invalidTransitionIsRejected() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");
        action(id, "complete").andExpect(status().isOk());

        cancel(id, "EARLY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        action(id, "no-show")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("sessão cancelada deixa de ocupar horário: o aviso de conflito some na outra")
    void cancellingRemovesTheOverlapWarning() throws Exception {
        String maria = sessionFor("Maria Silva", "09:00");
        String joao = sessionFor("João Souza", "09:00");

        cancel(maria, "EARLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapsWith.length()").value(0));

        mvc.perform(get("/api/sessions/" + joao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapsWith.length()").value(0));
    }

    @Test
    @DisplayName("sessão inexistente: 404 em todas as ações")
    void unknownSessionIsNotFound() throws Exception {
        String unknown = UUID.randomUUID().toString();

        action(unknown, "confirm").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        action(unknown, "complete").andExpect(status().isNotFound());
        action(unknown, "no-show").andExpect(status().isNotFound());
        cancel(unknown, "EARLY").andExpect(status().isNotFound());
        payment(unknown, "PAID").andExpect(status().isNotFound());
    }

    // ---------- pagamento ----------

    @Test
    @DisplayName("marcar como pago: guarda quando; repetir não muda a data; desfazer volta a pendente")
    void paymentRoundTrip() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        payment(id, "PAID")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        String firstPaidAt = paidAtInDatabase(id);

        payment(id, "PAID").andExpect(status().isOk());
        assertThat(paidAtInDatabase(id)).isEqualTo(firstPaidAt);

        payment(id, "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.paidAt").doesNotExist());
        assertThat(paidAtInDatabase(id)).isNull();
    }

    @Test
    @DisplayName("sessão cancelada com antecedência não recebe pagamento: 422 SESSION_NOT_BILLABLE")
    void notBillableSessionCannotBePaid() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");
        cancel(id, "EARLY").andExpect(status().isOk());

        payment(id, "PAID")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"));
    }

    @Test
    @DisplayName("cancelar com antecedência uma sessão já paga: continua paga (a tela avisa) e dá para desfazer")
    void cancellingAPaidSessionKeepsThePayment() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");
        payment(id, "PAID").andExpect(status().isOk());

        cancel(id, "EARLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.billable").value(false));

        payment(id, "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    @Test
    @DisplayName("pagamento com situação desconhecida ou sem corpo: 400 VALIDATION_ERROR")
    void paymentRequiresAValidStatus() throws Exception {
        String id = sessionFor("Maria Silva", "09:00");

        payment(id, "METADE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/sessions/" + id + "/payment").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
