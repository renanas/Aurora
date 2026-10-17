package br.com.aurora.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * Agenda (entrega D) contra PostgreSQL real: sessão avulsa, corrigir horário, reagendar, desfazer e reabrir,
 * incluindo o aviso de conflito (409) e o reenvio com confirmOverlap.
 * Datas relativas a "amanhã"; a sessão de cada paciente é a primeira gerada (amanhã às 09:00 ou ao horário dado).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SessionPlanningApiTest {

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

    private String sessionOfNewPatient(String name, String time) throws Exception {
        String patientId = createPatient(name);
        mvc.perform(put("/api/patients/" + patientId + "/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchorDate\":\"" + tomorrow + "\",\"startTime\":\"" + time
                                + "\",\"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id::text FROM therapy_session WHERE patient_id = ? ORDER BY starts_at LIMIT 1",
                String.class, UUID.fromString(patientId));
    }

    private static String at(LocalDate date, String time) {
        return date + "T" + time;
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createSession(String patientId, String startsAt, boolean confirm) throws Exception {
        return json(post("/api/sessions"), "{\"patientId\":\"" + patientId + "\",\"startsAt\":\"" + startsAt
                + "\",\"confirmOverlap\":" + confirm + "}");
    }

    private ResultActions changeTime(String id, String startsAt, boolean confirm) throws Exception {
        return json(patch("/api/sessions/" + id + "/time"),
                "{\"startsAt\":\"" + startsAt + "\",\"confirmOverlap\":" + confirm + "}");
    }

    private ResultActions reschedule(String id, String startsAt, boolean confirm) throws Exception {
        return json(post("/api/sessions/" + id + "/reschedule"),
                "{\"startsAt\":\"" + startsAt + "\",\"confirmOverlap\":" + confirm + "}");
    }

    private ResultActions undo(String newSessionId, boolean confirm) throws Exception {
        return json(post("/api/sessions/" + newSessionId + "/undo-reschedule"),
                "{\"confirmOverlap\":" + confirm + "}");
    }

    private ResultActions reopen(String id, boolean confirm) throws Exception {
        return json(post("/api/sessions/" + id + "/reopen"), "{\"confirmOverlap\":" + confirm + "}");
    }

    private ResultActions action(String id, String name) throws Exception {
        return mvc.perform(post("/api/sessions/" + id + "/" + name));
    }

    private String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private int sessionCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class);
        return n == null ? 0 : n;
    }

    private String columnOf(String id, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM therapy_session WHERE id = ?",
                String.class, UUID.fromString(id));
    }

    // ---------- sessão avulsa ----------

    @Test
    @DisplayName("sessão avulsa: 201, agendada, com o valor do paciente e sem recorrência")
    void createStandalone() throws Exception {
        String patientId = createPatient("Maria Silva");

        createSession(patientId, at(tomorrow, "15:30"), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientName").value("Maria Silva"))
                .andExpect(jsonPath("$.startsAt").value(at(tomorrow, "15:30")))
                .andExpect(jsonPath("$.endsAt").value(at(tomorrow, "16:20")))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(180.0))
                .andExpect(jsonPath("$.recurrenceId").doesNotExist())
                .andExpect(jsonPath("$.rescheduledFromId").doesNotExist());
    }

    @Test
    @DisplayName("sessão avulsa: paciente inexistente (404), inativo (422) e dados inválidos (400/422)")
    void createStandaloneValidation() throws Exception {
        String inactive = createPatient("Maria Silva");
        mvc.perform(post("/api/patients/" + inactive + "/deactivate")).andExpect(status().isOk());

        createSession(UUID.randomUUID().toString(), at(tomorrow, "10:00"), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        createSession(inactive, at(tomorrow, "10:00"), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));

        String active = createPatient("João Souza");
        createSession(active, "28/09/2026 10:00", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        createSession(active, "2026-02-30T10:00", false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_TIME"));
        assertThat(sessionCount()).isZero();
    }

    @Test
    @DisplayName("sessão avulsa em cima de outra: 409 com a lista; com confirmOverlap grava e avisa nas duas")
    void createStandaloneOverlap() throws Exception {
        sessionOfNewPatient("Maria Silva", "09:00");
        String joao = createPatient("João Souza");

        createSession(joao, at(tomorrow, "09:30"), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].patientName").value("Maria Silva"))
                .andExpect(jsonPath("$.conflicts[0].startsAt").value(at(tomorrow, "09:00")));
        assertThat(sessionCount()).isEqualTo(8);

        createSession(joao, at(tomorrow, "09:30"), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overlapsWith.length()").value(1));
        assertThat(sessionCount()).isEqualTo(9);
    }

    // ---------- corrigir horário ----------

    @Test
    @DisplayName("corrigir horário: muda o início, continua da recorrência e com a mesma data de origem")
    void changeTimeKeepsTheOccurrence() throws Exception {
        String id = sessionOfNewPatient("Maria Silva", "09:00");
        String occurrenceBefore = columnOf(id, "occurrence_date");

        changeTime(id, at(tomorrow, "14:00"), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.startsAt").value(at(tomorrow, "14:00")))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.recurrenceId").isNotEmpty());

        assertThat(columnOf(id, "occurrence_date")).isEqualTo(occurrenceBefore);
        changeTime(id, at(tomorrow, "14:00"), false).andExpect(status().isOk());
        assertThat(sessionCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("corrigir horário de sessão que não está AGENDADA: 422 SESSION_NOT_SCHEDULED")
    void changeTimeNeedsAScheduledSession() throws Exception {
        String id = sessionOfNewPatient("Maria Silva", "09:00");
        action(id, "confirm").andExpect(status().isOk());

        changeTime(id, at(tomorrow, "14:00"), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_SCHEDULED"));
    }

    @Test
    @DisplayName("corrigir horário em cima de outra sessão: 409, e nada muda até confirmar")
    void changeTimeOverlap() throws Exception {
        String maria = sessionOfNewPatient("Maria Silva", "09:00");
        String joao = sessionOfNewPatient("João Souza", "11:00");

        changeTime(joao, at(tomorrow, "09:20"), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"));
        assertThat(columnOf(joao, "starts_at")).endsWith("11:00:00");

        changeTime(joao, at(tomorrow, "09:20"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapsWith[0]").value(maria));
    }

    // ---------- reagendar e desfazer ----------

    @Test
    @DisplayName("reagendar: original REAGENDADA, nova AGENDADA com o pagamento, recorrência intacta")
    void rescheduleMovesThePayment() throws Exception {
        String original = sessionOfNewPatient("Maria Silva", "09:00");
        mvc.perform(put("/api/sessions/" + original + "/payment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")).andExpect(status().isOk());

        String next = idOf(reschedule(original, at(tomorrow, "16:00"), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.startsAt").value(at(tomorrow, "16:00")))
                .andExpect(jsonPath("$.rescheduledFromId").value(original))
                .andExpect(jsonPath("$.recurrenceId").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(180.0)));

        mvc.perform(get("/api/sessions/" + original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULED"))
                .andExpect(jsonPath("$.billable").value(false))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        assertThat(next).isNotEqualTo(original);
        assertThat(sessionCount()).isEqualTo(9);
        Integer activeRecurrences = jdbc.queryForObject(
                "SELECT count(*) FROM recurrence WHERE ends_on IS NULL", Integer.class);
        assertThat(activeRecurrences).isEqualTo(1);
    }

    @Test
    @DisplayName("reagendar de novo o mesmo pedido: 200 com a mesma sessão nova, sem duplicar")
    void rescheduleIsIdempotent() throws Exception {
        String original = sessionOfNewPatient("Maria Silva", "09:00");

        String first = idOf(reschedule(original, at(tomorrow, "16:00"), false).andExpect(status().isOk()));
        String second = idOf(reschedule(original, at(tomorrow, "16:00"), false).andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        assertThat(sessionCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("reagendar: horário igual ao atual (422) ou sessão já reagendada para outro horário (422)")
    void rescheduleRules() throws Exception {
        String original = sessionOfNewPatient("Maria Silva", "09:00");

        reschedule(original, at(tomorrow, "09:00"), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESCHEDULE_SAME_TIME"));

        reschedule(original, at(tomorrow, "16:00"), false).andExpect(status().isOk());
        reschedule(original, at(tomorrow, "17:00"), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("reagendar para cima de outra sessão: 409, e a original continua intacta até confirmar")
    void rescheduleOverlap() throws Exception {
        String maria = sessionOfNewPatient("Maria Silva", "09:00");
        sessionOfNewPatient("João Souza", "11:00");

        reschedule(maria, at(tomorrow, "11:10"), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"))
                .andExpect(jsonPath("$.conflicts[0].patientName").value("João Souza"));
        assertThat(columnOf(maria, "status")).isEqualTo("SCHEDULED");
        assertThat(sessionCount()).isEqualTo(16);

        reschedule(maria, at(tomorrow, "11:10"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapsWith.length()").value(1));
        assertThat(sessionCount()).isEqualTo(17);
    }

    @Test
    @DisplayName("desfazer reagendamento: a original volta com o pagamento, a nova é cancelada, e dá para reagendar de novo")
    void undoReschedule() throws Exception {
        String original = sessionOfNewPatient("Maria Silva", "09:00");
        mvc.perform(put("/api/sessions/" + original + "/payment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")).andExpect(status().isOk());
        String next = idOf(reschedule(original, at(tomorrow, "16:00"), false).andExpect(status().isOk()));

        undo(next, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        mvc.perform(get("/api/sessions/" + next))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationType").value("EARLY"))
                .andExpect(jsonPath("$.billable").value(false))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        undo(next, false).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original));

        reschedule(original, at(tomorrow, "17:00"), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value(at(tomorrow, "17:00")));
    }

    @Test
    @DisplayName("desfazer reagendamento: não vale em sessão comum nem depois de a nova ser confirmada")
    void undoRescheduleRules() throws Exception {
        String plain = sessionOfNewPatient("Maria Silva", "09:00");
        undo(plain, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNDO_RESCHEDULE_NOT_ALLOWED"));

        String next = idOf(reschedule(plain, at(tomorrow, "16:00"), false).andExpect(status().isOk()));
        action(next, "confirm").andExpect(status().isOk());
        undo(next, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNDO_RESCHEDULE_NOT_ALLOWED"));
    }

    // ---------- reabrir ----------

    @Test
    @DisplayName("reabrir: realizada, falta e cancelada voltam a AGENDADA; cancelada perde o tipo")
    void reopen() throws Exception {
        String completed = sessionOfNewPatient("Maria Silva", "09:00");
        String noShow = sessionOfNewPatient("João Souza", "11:00");
        String cancelled = sessionOfNewPatient("Ana Lima", "14:00");
        action(completed, "complete").andExpect(status().isOk());
        action(noShow, "no-show").andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + cancelled + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancellationType\":\"LATE\"}")).andExpect(status().isOk());

        reopen(completed, false).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        reopen(noShow, false).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        reopen(cancelled, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancellationType").doesNotExist())
                .andExpect(jsonPath("$.billable").value(true));
        reopen(cancelled, false).andExpect(status().isOk());
    }

    @Test
    @DisplayName("reabrir sessão confirmada ou reagendada: 422 REOPEN_NOT_ALLOWED")
    void reopenRules() throws Exception {
        String confirmed = sessionOfNewPatient("Maria Silva", "09:00");
        action(confirmed, "confirm").andExpect(status().isOk());
        reopen(confirmed, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REOPEN_NOT_ALLOWED"));

        String moved = sessionOfNewPatient("João Souza", "11:00");
        reschedule(moved, at(tomorrow, "16:00"), false).andExpect(status().isOk());
        reopen(moved, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REOPEN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("reabrir cancelada em horário já ocupado: 409, e só reabre depois de confirmar")
    void reopenOverlap() throws Exception {
        String maria = sessionOfNewPatient("Maria Silva", "09:00");
        mvc.perform(post("/api/sessions/" + maria + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancellationType\":\"EARLY\"}")).andExpect(status().isOk());
        sessionOfNewPatient("João Souza", "09:00");

        reopen(maria, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"));
        assertThat(columnOf(maria, "status")).isEqualTo("CANCELLED");

        reopen(maria, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.overlapsWith.length()").value(1));
    }

    @Test
    @DisplayName("sessão inexistente: 404 em todas as ações da agenda")
    void unknownSessionIsNotFound() throws Exception {
        String unknown = UUID.randomUUID().toString();

        changeTime(unknown, at(tomorrow, "10:00"), false).andExpect(status().isNotFound());
        reschedule(unknown, at(tomorrow, "10:00"), false).andExpect(status().isNotFound());
        undo(unknown, false).andExpect(status().isNotFound());
        reopen(unknown, false).andExpect(status().isNotFound());
    }
}
