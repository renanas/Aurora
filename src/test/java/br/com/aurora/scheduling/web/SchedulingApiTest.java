package br.com.aurora.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.scheduling.application.SessionGenerator;
import br.com.aurora.shared.error.RequestIdFilter;
import br.com.aurora.shared.time.SystemConstants;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
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
 * Recorrência, geração de sessões e agenda, contra PostgreSQL real.
 * Os testes usam datas relativas a "hoje" (relógio real) e a âncora "amanhã", para que a contagem
 * de sessões dentro do horizonte de 8 semanas seja a mesma em qualquer dia da semana:
 * semanal = 8 sessões (dias +1, +8, ..., +50); quinzenal = 4 sessões (dias +1, +15, +29, +43).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SchedulingApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SessionGenerator generator;

    MockMvc mvc;
    LocalDate today;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
        today = LocalDate.now(SystemConstants.ZONE);
        tomorrow = today.plusDays(1);
    }

    // ---------- helpers ----------

    private static String patientJson(String name, String price) {
        return "{\"name\":\"" + name + "\",\"whatsapp\":\"5511999998888\",\"sessionPrice\":" + price + "}";
    }

    private String createPatient(String name) throws Exception {
        String body = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson(name, "180")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    private ResultActions putRecurrence(String patientId, LocalDate anchor, String time, String frequency)
            throws Exception {
        return mvc.perform(put("/api/patients/" + patientId + "/recurrence")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anchorDate\":\"" + anchor + "\",\"startTime\":\"" + time
                        + "\",\"frequency\":\"" + frequency + "\"}"));
    }

    private String createRecurrence(String patientId, LocalDate anchor, String time, String frequency)
            throws Exception {
        String body = putRecurrence(patientId, anchor, time, frequency)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    private int sessionCount(String patientId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM therapy_session WHERE patient_id = ?",
                Integer.class, UUID.fromString(patientId));
        return n == null ? 0 : n;
    }

    private int recurrenceCount(String patientId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM recurrence WHERE patient_id = ?",
                Integer.class, UUID.fromString(patientId));
        return n == null ? 0 : n;
    }

    private int activeRecurrenceCount(String patientId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM recurrence WHERE patient_id = ? AND ends_on IS NULL",
                Integer.class, UUID.fromString(patientId));
        return n == null ? 0 : n;
    }

    private void updateFirstSession(String patientId, String setClause) {
        jdbc.update("UPDATE therapy_session SET " + setClause + " WHERE id = ("
                        + "SELECT id FROM therapy_session WHERE patient_id = ? ORDER BY starts_at LIMIT 1)",
                UUID.fromString(patientId));
    }

    // ---------- recorrência e geração ----------

    @Test
    @DisplayName("recorrência semanal gera as sessões até o horizonte de 8 semanas")
    void weeklyRecurrenceGeneratesSessions() throws Exception {
        String patientId = createPatient("Maria Silva");

        putRecurrence(patientId, tomorrow, "09:00", "WEEKLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorDate").value(tomorrow.toString()))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(sessionCount(patientId)).isEqualTo(8);

        mvc.perform(get("/api/sessions").param("from", tomorrow.toString()).param("to", today.plusDays(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].startsAt").value(tomorrow + "T09:00"))
                .andExpect(jsonPath("$[0].endsAt").value(tomorrow + "T09:50"))
                .andExpect(jsonPath("$[0].patientName").value("Maria Silva"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].durationMinutes").value(50))
                .andExpect(jsonPath("$[0].amount").value(180.0))
                .andExpect(jsonPath("$[0].billable").value(true))
                .andExpect(jsonPath("$[0].overlapsWith.length()").value(0));
    }

    @Test
    @DisplayName("recorrência quinzenal gera uma sessão a cada 14 dias")
    void biweeklyRecurrenceGeneratesSessions() throws Exception {
        String patientId = createPatient("Maria Silva");

        createRecurrence(patientId, tomorrow, "09:00", "BIWEEKLY");

        assertThat(sessionCount(patientId)).isEqualTo(4);
    }

    @Test
    @DisplayName("âncora além do horizonte: a recorrência existe, mas ainda não gera sessões")
    void anchorBeyondHorizonGeneratesNothingYet() throws Exception {
        String patientId = createPatient("Maria Silva");

        createRecurrence(patientId, today.plusDays(90), "09:00", "WEEKLY");

        assertThat(recurrenceCount(patientId)).isEqualTo(1);
        assertThat(sessionCount(patientId)).isZero();
    }

    @Test
    @DisplayName("repetir exatamente o mesmo horário não muda nada")
    void sameScheduleIsIdempotent() throws Exception {
        String patientId = createPatient("Maria Silva");
        String firstId = createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        String secondId = createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(recurrenceCount(patientId)).isEqualTo(1);
        assertThat(sessionCount(patientId)).isEqualTo(8);
    }

    @Test
    @DisplayName("gerar de novo não duplica sessões")
    void generationIsIdempotent() throws Exception {
        String patientId = createPatient("Maria Silva");
        String recurrenceId = createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        int created = generator.generateFor(UUID.fromString(recurrenceId));

        assertThat(created).isZero();
        assertThat(sessionCount(patientId)).isEqualTo(8);
    }

    @Test
    @DisplayName("trocar o horário: o antigo é encerrado na véspera e o novo gera a partir da nova data")
    void replacingRecurrenceEndsTheOldOne() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        LocalDate newAnchor = today.plusDays(15);
        putRecurrence(patientId, newAnchor, "10:00", "WEEKLY").andExpect(status().isOk());

        // antigo: sessões dos dias +1 e +8 ficam (antes da nova âncora); novo: +15, +22, ..., +50 (6 sessões)
        assertThat(recurrenceCount(patientId)).isEqualTo(2);
        assertThat(activeRecurrenceCount(patientId)).isEqualTo(1);
        assertThat(sessionCount(patientId)).isEqualTo(8);

        Integer ended = jdbc.queryForObject(
                "SELECT count(*) FROM recurrence WHERE patient_id = ? AND ends_on = ?",
                Integer.class, UUID.fromString(patientId), newAnchor.minusDays(1));
        assertThat(ended).isEqualTo(1);

        Integer atTen = jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND CAST(starts_at AS time) = TIME '10:00'",
                Integer.class, UUID.fromString(patientId));
        assertThat(atTen).isEqualTo(6);
    }

    @Test
    @DisplayName("corrigir um horário recém-criado (nada aconteceu): a recorrência antiga é apagada, sem sobras")
    void correctingAFreshRecurrenceLeavesNoLeftovers() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        putRecurrence(patientId, tomorrow, "10:00", "WEEKLY").andExpect(status().isOk());

        assertThat(recurrenceCount(patientId)).isEqualTo(1);
        assertThat(sessionCount(patientId)).isEqualTo(8);
        Integer atTen = jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND CAST(starts_at AS time) = TIME '10:00'",
                Integer.class, UUID.fromString(patientId));
        assertThat(atTen).isEqualTo(8);
    }

    @Test
    @DisplayName("nova data que não é posterior à atual, havendo sessão que não pode sumir: 422 e nada muda")
    void anchorNotAfterCurrentIsRejectedAndRollsBack() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");
        updateFirstSession(patientId, "status = 'CONFIRMED'");

        putRecurrence(patientId, tomorrow, "10:00", "WEEKLY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECURRENCE_ANCHOR_NOT_AFTER_CURRENT"));

        assertThat(sessionCount(patientId)).isEqualTo(8);
        assertThat(activeRecurrenceCount(patientId)).isEqualTo(1);
    }

    @Test
    @DisplayName("sessões que já foram mexidas (confirmada, paga) não são apagadas ao trocar o horário")
    void touchedSessionsSurviveAReplacement() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");
        // a de +50 dias é a última; confirma a de +36 e marca a de +43 como paga
        jdbc.update("UPDATE therapy_session SET status = 'CONFIRMED' WHERE patient_id = ? AND starts_at::date = ?",
                UUID.fromString(patientId), today.plusDays(36));
        jdbc.update("UPDATE therapy_session SET payment_status = 'PAID', paid_at = now() "
                        + "WHERE patient_id = ? AND starts_at::date = ?",
                UUID.fromString(patientId), today.plusDays(43));

        putRecurrence(patientId, today.plusDays(15), "10:00", "WEEKLY").andExpect(status().isOk());

        // Do horário antigo (09:00) ficam: +1 e +8 (antes da nova âncora) e as duas mexidas, +36 e +43 = 4.
        // As demais (+15, +22, +29, +50) foram apagadas; o novo horário (10:00) gera as suas próprias.
        Integer survivors = jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND CAST(starts_at AS time) = TIME '09:00'",
                Integer.class, UUID.fromString(patientId));
        assertThat(survivors).isEqualTo(4);
    }

    @Test
    @DisplayName("encerrar o horário: sessões futuras intocadas somem e a recorrência deixa de existir como ativa")
    void endingRemovesFutureUntouchedSessions() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        mvc.perform(post("/api/patients/" + patientId + "/recurrence/end")).andExpect(status().isNoContent());

        assertThat(sessionCount(patientId)).isZero();
        assertThat(activeRecurrenceCount(patientId)).isZero();
        mvc.perform(get("/api/patients/" + patientId + "/recurrence")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("encerrar sem horário ativo: 422 NO_ACTIVE_RECURRENCE")
    void endingWithoutActiveRecurrenceFails() throws Exception {
        String patientId = createPatient("Maria Silva");

        mvc.perform(post("/api/patients/" + patientId + "/recurrence/end"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_RECURRENCE"));
    }

    @Test
    @DisplayName("inativar o paciente encerra o horário e remove as sessões futuras intocadas")
    void deactivatingThePatientEndsTheRecurrence() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");

        mvc.perform(post("/api/patients/" + patientId + "/deactivate")).andExpect(status().isOk());

        assertThat(activeRecurrenceCount(patientId)).isZero();
        assertThat(sessionCount(patientId)).isZero();
    }

    @Test
    @DisplayName("paciente inativo não recebe horário recorrente: 422 PATIENT_INACTIVE")
    void inactivePatientCannotGetARecurrence() throws Exception {
        String patientId = createPatient("Maria Silva");
        mvc.perform(post("/api/patients/" + patientId + "/deactivate")).andExpect(status().isOk());

        putRecurrence(patientId, tomorrow, "09:00", "WEEKLY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATIENT_INACTIVE"));
    }

    @Test
    @DisplayName("mudar o valor do paciente atualiza só as futuras com pagamento pendente")
    void priceChangeUpdatesOnlyFuturePendingSessions() throws Exception {
        String patientId = createPatient("Maria Silva");
        createRecurrence(patientId, tomorrow, "09:00", "WEEKLY");
        updateFirstSession(patientId, "payment_status = 'PAID', paid_at = now()");

        mvc.perform(put("/api/patients/" + patientId).contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson("Maria Silva", "200")))
                .andExpect(status().isOk());

        Integer updated = jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND amount = 200",
                Integer.class, UUID.fromString(patientId));
        Integer untouched = jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND amount = 180",
                Integer.class, UUID.fromString(patientId));
        assertThat(updated).isEqualTo(7);
        assertThat(untouched).isEqualTo(1);
    }

    @Test
    @DisplayName("paciente inexistente: 404 ao consultar o horário")
    void unknownPatientIsNotFound() throws Exception {
        mvc.perform(get("/api/patients/" + UUID.randomUUID() + "/recurrence"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("horário fora do formato HH:mm ou frequência desconhecida: 400 VALIDATION_ERROR")
    void invalidRecurrenceInputIsRejected() throws Exception {
        String patientId = createPatient("Maria Silva");

        putRecurrence(patientId, tomorrow, "9:00", "WEEKLY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        putRecurrence(patientId, tomorrow, "09:00", "DAILY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(recurrenceCount(patientId)).isZero();
    }

    // ---------- leitura da agenda ----------

    @Test
    @DisplayName("duas sessões no mesmo horário aparecem com overlapsWith, uma apontando para a outra")
    void overlappingSessionsAreReported() throws Exception {
        String maria = createPatient("Maria Silva");
        String joao = createPatient("João Souza");
        createRecurrence(maria, tomorrow, "09:00", "WEEKLY");
        createRecurrence(joao, tomorrow, "09:30", "WEEKLY");

        String body = mvc.perform(get("/api/sessions").param("from", tomorrow.toString()).param("to", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].overlapsWith.length()").value(1))
                .andExpect(jsonPath("$[1].overlapsWith.length()").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String firstId = JsonPath.read(body, "$[0].id");
        String secondId = JsonPath.read(body, "$[1].id");
        mvc.perform(get("/api/sessions/" + firstId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapsWith[0]").value(secondId));
    }

    @Test
    @DisplayName("sessão cancelada não ocupa horário: não gera aviso de sobreposição")
    void cancelledSessionsDoNotOverlap() throws Exception {
        String maria = createPatient("Maria Silva");
        String joao = createPatient("João Souza");
        createRecurrence(maria, tomorrow, "09:00", "WEEKLY");
        createRecurrence(joao, tomorrow, "09:00", "WEEKLY");
        jdbc.update("UPDATE therapy_session SET status = 'CANCELLED', cancellation_type = 'EARLY' "
                + "WHERE patient_id = ?", UUID.fromString(joao));

        mvc.perform(get("/api/sessions").param("from", tomorrow.toString()).param("to", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].overlapsWith.length()").value(0))
                .andExpect(jsonPath("$[1].overlapsWith.length()").value(0));
    }

    @Test
    @DisplayName("intervalo inválido (invertido ou acima de 100 dias): 422 INVALID_DATE_RANGE")
    void invalidRangeIsRejected() throws Exception {
        mvc.perform(get("/api/sessions").param("from", today.plusDays(10).toString()).param("to", today.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        mvc.perform(get("/api/sessions").param("from", today.toString()).param("to", today.plusDays(100).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        mvc.perform(get("/api/sessions").param("from", today.toString()).param("to", today.plusDays(99).toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sessão inexistente: 404")
    void unknownSessionIsNotFound() throws Exception {
        mvc.perform(get("/api/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
