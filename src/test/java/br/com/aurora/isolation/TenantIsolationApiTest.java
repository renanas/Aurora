package br.com.aurora.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestPsychologistConfig;
import br.com.aurora.TestPsychologists;
import br.com.aurora.TestcontainersConfig;
import br.com.aurora.scheduling.application.SessionGenerator;
import br.com.aurora.shared.error.RequestIdFilter;
import br.com.aurora.shared.time.SystemConstants;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Cada psicóloga só enxerga o que é seu. A Ana cria paciente, horário recorrente e sessões; a Bia tenta
 * todos os endpoints com os ids da Ana. Esperado: 404 (não 403, para não revelar que existe), listas sem
 * nada da Ana, nada alterado no banco, e o aviso de conflito de horário sem vazar entre contas.
 * Também confere que o banco recusa dono diferente entre sessão e paciente.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, TestPsychologistConfig.class})
class TenantIsolationApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestPsychologists psychologists;
    @Autowired
    SessionGenerator generator;

    MockMvc mvc;
    UUID ana;
    UUID bia;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        cleanDatabase();
        ana = psychologists.create("ana@isolamento.test");
        bia = psychologists.create("bia@isolamento.test");
        psychologists.as(ana);
        tomorrow = LocalDate.now(SystemConstants.ZONE).plusDays(1);
    }

    /** O banco é compartilhado entre as classes de teste: não deixar linhas para o próximo. */
    @AfterEach
    void tearDown() {
        psychologists.reset();
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
    }

    // ---------- helpers ----------

    private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createPatient(String name) throws Exception {
        String body = json(post("/api/patients"),
                "{\"name\":\"" + name + "\",\"whatsapp\":\"5511999998888\",\"sessionPrice\":180}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    private void setRecurrence(String patientId, String time) throws Exception {
        json(put("/api/patients/" + patientId + "/recurrence"),
                "{\"anchorDate\":\"" + tomorrow + "\",\"startTime\":\"" + time + "\",\"frequency\":\"WEEKLY\"}")
                .andExpect(status().isOk());
    }

    private String firstSessionOf(String patientId) {
        return jdbc.queryForObject(
                "SELECT id::text FROM therapy_session WHERE patient_id = ? ORDER BY starts_at LIMIT 1",
                String.class, UUID.fromString(patientId));
    }

    private void assertNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** O que a Ana criou: paciente com horário recorrente (sessões geradas) e o id da primeira sessão. */
    private record AnaData(String patientId, String sessionId) {
    }

    private AnaData anaCreatesPatientWithSessions() throws Exception {
        String patientId = createPatient("Maria da Ana");
        setRecurrence(patientId, "09:00");
        return new AnaData(patientId, firstSessionOf(patientId));
    }

    // ---------- leituras: listas sem nada da outra conta ----------

    @Test
    @DisplayName("a Bia não vê pacientes nem sessões da Ana nas listas, nem pela busca")
    void listsDoNotLeak() throws Exception {
        anaCreatesPatientWithSessions();
        psychologists.as(bia);

        mvc.perform(get("/api/patients")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/patients").param("search", "Maria")).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/patients").param("search", "maria da ana")).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/patients").param("active", "false")).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/sessions").param("from", tomorrow.toString()).param("to", tomorrow.plusDays(60).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("a Ana continua vendo o que é dela")
    void ownerStillSeesHerData() throws Exception {
        anaCreatesPatientWithSessions();

        mvc.perform(get("/api/patients")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/sessions").param("from", tomorrow.toString()).param("to", tomorrow.plusDays(60).toString()))
                .andExpect(jsonPath("$.length()").value(8));
    }

    // ---------- pacientes ----------

    @Test
    @DisplayName("paciente da Ana: a Bia recebe 404 em tudo e nada muda no banco")
    void patientEndpointsAreNotFoundForOthers() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        String id = data.patientId();
        psychologists.as(bia);

        assertNotFound(mvc.perform(get("/api/patients/" + id)));
        assertNotFound(json(put("/api/patients/" + id),
                "{\"name\":\"Invasora\",\"whatsapp\":\"5511999990000\",\"sessionPrice\":1}"));
        assertNotFound(mvc.perform(post("/api/patients/" + id + "/deactivate")));
        assertNotFound(mvc.perform(post("/api/patients/" + id + "/activate")));

        assertThat(jdbc.queryForObject("SELECT name FROM patient WHERE id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("Maria da Ana");
        assertThat(jdbc.queryForObject("SELECT active FROM patient WHERE id = ?", Boolean.class, UUID.fromString(id)))
                .isTrue();
    }

    @Test
    @DisplayName("o paciente criado pertence a quem estava logada")
    void createdPatientBelongsToTheCurrentAccount() throws Exception {
        psychologists.as(bia);
        String id = createPatient("Paciente da Bia");

        assertThat(jdbc.queryForObject("SELECT psychologist_id FROM patient WHERE id = ?", UUID.class,
                UUID.fromString(id))).isEqualTo(bia);
    }

    // ---------- recorrência ----------

    @Test
    @DisplayName("recorrência da Ana: a Bia recebe 404 em tudo e a recorrência fica como estava")
    void recurrenceEndpointsAreNotFoundForOthers() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        String base = "/api/patients/" + data.patientId() + "/recurrence";
        psychologists.as(bia);

        assertNotFound(mvc.perform(get(base)));
        assertNotFound(json(put(base),
                "{\"anchorDate\":\"" + tomorrow + "\",\"startTime\":\"15:00\",\"frequency\":\"BIWEEKLY\"}"));
        assertNotFound(mvc.perform(post(base + "/end")));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recurrence WHERE patient_id = ? AND ends_on IS NULL AND start_time = '09:00'",
                Integer.class, UUID.fromString(data.patientId()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM therapy_session WHERE patient_id = ?",
                Integer.class, UUID.fromString(data.patientId()))).isEqualTo(8);
    }

    @Test
    @DisplayName("recorrência e sessões geradas ficam com a mesma dona do paciente")
    void recurrenceAndGeneratedSessionsCopyTheOwner() throws Exception {
        psychologists.as(bia);
        String patientId = createPatient("Paciente da Bia");
        setRecurrence(patientId, "10:00");

        assertThat(jdbc.queryForObject("SELECT psychologist_id FROM recurrence WHERE patient_id = ?", UUID.class,
                UUID.fromString(patientId))).isEqualTo(bia);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM therapy_session WHERE patient_id = ? AND psychologist_id <> ?",
                Integer.class, UUID.fromString(patientId), bia)).isZero();
    }

    @Test
    @DisplayName("a geração automática de sessões mantém a dona da recorrência, sem depender de quem está logada")
    void generationKeepsTheRecurrenceOwner() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        UUID recurrenceId = jdbc.queryForObject("SELECT id FROM recurrence WHERE patient_id = ?", UUID.class,
                UUID.fromString(data.patientId()));
        jdbc.update("DELETE FROM therapy_session WHERE id = ?", UUID.fromString(data.sessionId()));
        psychologists.as(bia); // o job não tem "conta logada"; mesmo assim a sessão nova é da Ana

        int created = generator.generateFor(recurrenceId);

        assertThat(created).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM therapy_session WHERE psychologist_id <> ?",
                Integer.class, ana)).isZero();
    }

    // ---------- sessões ----------

    @Test
    @DisplayName("sessão da Ana: a Bia recebe 404 em todas as ações e nada muda no banco")
    void sessionEndpointsAreNotFoundForOthers() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        String base = "/api/sessions/" + data.sessionId();
        String when = tomorrow.plusDays(3) + "T14:00";
        psychologists.as(bia);

        assertNotFound(mvc.perform(get(base)));
        assertNotFound(mvc.perform(post(base + "/confirm")));
        assertNotFound(mvc.perform(post(base + "/complete")));
        assertNotFound(mvc.perform(post(base + "/no-show")));
        assertNotFound(json(post(base + "/cancel"), "{\"cancellationType\":\"EARLY\"}"));
        assertNotFound(json(put(base + "/payment"), "{\"status\":\"PAID\"}"));
        assertNotFound(json(patch(base + "/time"), "{\"startsAt\":\"" + when + "\",\"confirmOverlap\":true}"));
        assertNotFound(json(post(base + "/reschedule"), "{\"startsAt\":\"" + when + "\",\"confirmOverlap\":true}"));
        assertNotFound(json(post(base + "/undo-reschedule"), "{\"confirmOverlap\":true}"));
        assertNotFound(json(post(base + "/reopen"), "{\"confirmOverlap\":true}"));

        assertThat(jdbc.queryForObject("SELECT status || '/' || payment_status FROM therapy_session WHERE id = ?",
                String.class, UUID.fromString(data.sessionId()))).isEqualTo("SCHEDULED/PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class)).isEqualTo(8);
    }

    @Test
    @DisplayName("a Bia não consegue criar sessão para paciente da Ana")
    void cannotCreateSessionForAnotherAccountsPatient() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        int before = jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class);
        psychologists.as(bia);

        assertNotFound(json(post("/api/sessions"), "{\"patientId\":\"" + data.patientId() + "\",\"startsAt\":\""
                + tomorrow.plusDays(2) + "T16:00\",\"confirmOverlap\":true}"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("sessão avulsa criada pela Bia é dela")
    void standaloneSessionBelongsToTheCurrentAccount() throws Exception {
        psychologists.as(bia);
        String patientId = createPatient("Paciente da Bia");

        String body = json(post("/api/sessions"), "{\"patientId\":\"" + patientId + "\",\"startsAt\":\""
                + tomorrow.plusDays(2) + "T16:00\",\"confirmOverlap\":false}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String sessionId = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("SELECT psychologist_id FROM therapy_session WHERE id = ?", UUID.class,
                UUID.fromString(sessionId))).isEqualTo(bia);
    }

    // ---------- conflito de horário ----------

    @Test
    @DisplayName("o aviso de conflito não vaza entre contas: mesmo horário da Ana não incomoda a Bia")
    void overlapWarningDoesNotLeakAcrossAccounts() throws Exception {
        anaCreatesPatientWithSessions(); // a Ana atende amanhã às 09:00
        psychologists.as(bia);
        String patientId = createPatient("Paciente da Bia");

        String body = json(post("/api/sessions"), "{\"patientId\":\"" + patientId + "\",\"startsAt\":\""
                + tomorrow + "T09:00\",\"confirmOverlap\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overlapsWith").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("Maria da Ana");
    }

    @Test
    @DisplayName("o conflito continua valendo dentro da mesma conta")
    void overlapWarningStillWorksForTheSameAccount() throws Exception {
        anaCreatesPatientWithSessions();
        String other = createPatient("Outra da Ana");

        json(post("/api/sessions"), "{\"patientId\":\"" + other + "\",\"startsAt\":\"" + tomorrow
                + "T09:00\",\"confirmOverlap\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAP_DETECTED"));
    }

    // ---------- o banco também recusa dono diferente ----------

    @Test
    @DisplayName("o banco recusa sessão cuja dona não é a do paciente")
    void databaseRejectsSessionWithAnotherOwnerThanThePatient() throws Exception {
        String patientId = createPatient("Maria da Ana");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (patient_id, psychologist_id, starts_at, amount) VALUES (?, ?, ?, 100)",
                UUID.fromString(patientId), bia, LocalDateTime.of(tomorrow, java.time.LocalTime.of(9, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa recorrência cuja dona não é a do paciente")
    void databaseRejectsRecurrenceWithAnotherOwnerThanThePatient() throws Exception {
        String patientId = createPatient("Maria da Ana");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO recurrence (patient_id, psychologist_id, anchor_date, start_time, frequency) "
                        + "VALUES (?, ?, ?, '09:00', 'WEEKLY')",
                UUID.fromString(patientId), bia, tomorrow))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa reagendamento ligado a sessão de outra conta")
    void databaseRejectsRescheduleLinkedToAnotherAccountsSession() throws Exception {
        AnaData data = anaCreatesPatientWithSessions();
        psychologists.as(bia);
        String biasPatient = createPatient("Paciente da Bia");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (patient_id, psychologist_id, rescheduled_from_id, starts_at, amount) "
                        + "VALUES (?, ?, ?, ?, 100)",
                UUID.fromString(biasPatient), bia, UUID.fromString(data.sessionId()),
                LocalDateTime.of(tomorrow.plusDays(2), java.time.LocalTime.of(9, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
