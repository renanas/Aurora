package br.com.aurora.scheduling.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aurora.TestPsychologistConfig;
import br.com.aurora.TestPsychologists;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * GET /api/earnings contra PostgreSQL real. Datas relativas a hoje (relógio real): sessões avulsas no passado
 * recente e no futuro próximo, levadas a cada situação pelos endpoints da agenda.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, TestPsychologistConfig.class})
class EarningsApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestPsychologists psychologists;

    MockMvc mvc;
    UUID ana;
    UUID bia;
    LocalDate today;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        cleanDatabase();
        ana = psychologists.create("ana@ganhos.test");
        bia = psychologists.create("bia@ganhos.test");
        psychologists.as(ana);
        today = LocalDate.now(SystemConstants.ZONE);
    }

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

    /** Sessão avulsa às 09:00 do dia {@code today + offset}. */
    private String session(String patientId, int offset) throws Exception {
        String body = json(post("/api/sessions"),
                "{\"patientId\":\"" + patientId + "\",\"startsAt\":\"" + today.plusDays(offset)
                        + "T09:00\",\"confirmOverlap\":true}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    private void action(String sessionId, String action) throws Exception {
        json(post("/api/sessions/" + sessionId + "/" + action), "{}").andExpect(status().isOk());
    }

    private void pay(String sessionId) throws Exception {
        json(put("/api/sessions/" + sessionId + "/payment"), "{\"status\":\"PAID\"}").andExpect(status().isOk());
    }

    private void cancel(String sessionId, String type) throws Exception {
        json(post("/api/sessions/" + sessionId + "/cancel"), "{\"cancellationType\":\"" + type + "\"}")
                .andExpect(status().isOk());
    }

    private ResultActions earnings(LocalDate from, LocalDate to, String groupBy) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/earnings")
                .param("from", from.toString()).param("to", to.toString());
        if (groupBy != null) {
            request.param("groupBy", groupBy);
        }
        return mvc.perform(request);
    }

    // ---------- testes ----------

    @Test
    @DisplayName("as três faixas, o aviso de pagas não cobráveis, o alerta sem desfecho e a lista a receber")
    void earningsOfThePeriod() throws Exception {
        String maria = createPatient("Maria");
        String paid = session(maria, -5);
        action(paid, "complete");
        pay(paid);                                   // recebido: 180
        action(session(maria, -4), "no-show");       // a receber: 180
        session(maria, -3);                          // previsto, sem desfecho: 180
        String early = session(maria, -2);
        pay(early);
        cancel(early, "EARLY");                      // fora das faixas, mas paga: aviso
        cancel(session(maria, -1), "LATE");          // a receber: 180
        session(maria, 2);                           // previsto: 180

        earnings(today.minusDays(10), today.plusDays(10), "DAY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("DAY"))
                .andExpect(jsonPath("$.totals.received").value(180.0))
                .andExpect(jsonPath("$.totals.receivable").value(360.0))
                .andExpect(jsonPath("$.totals.expected").value(360.0))
                .andExpect(jsonPath("$.totals.total").value(900.0))
                .andExpect(jsonPath("$.paidNotBillable.amount").value(180.0))
                .andExpect(jsonPath("$.paidNotBillable.count").value(1))
                .andExpect(jsonPath("$.sessionsWithoutOutcome").value(1))
                .andExpect(jsonPath("$.forecastIncomplete").value(false))
                .andExpect(jsonPath("$.periods.length()").value(21))
                .andExpect(jsonPath("$.periods[5].start").value(today.minusDays(5).toString()))
                .andExpect(jsonPath("$.periods[5].received").value(180.0))
                .andExpect(jsonPath("$.receivableSessions.length()").value(2))
                .andExpect(jsonPath("$.receivableSessions[0].patientName").value("Maria"))
                .andExpect(jsonPath("$.receivableSessions[0].status").value("NO_SHOW"))
                .andExpect(jsonPath("$.receivableSessions[0].startsAt").value(today.minusDays(4) + "T09:00"))
                .andExpect(jsonPath("$.receivableSessions[1].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("por semana: barras de segunda a domingo, cortadas pelo período")
    void groupsByWeek() throws Exception {
        LocalDate from = today.withDayOfMonth(1);
        LocalDate to = from.plusMonths(1).minusDays(1);

        earnings(from, to, "WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("WEEK"))
                .andExpect(jsonPath("$.periods[0].start").value(from.toString()))
                .andExpect(jsonPath("$.periods[-1].end").value(to.toString()))
                .andExpect(jsonPath("$.totals.total").value(0.0));
    }

    @Test
    @DisplayName("período além do horizonte de 8 semanas avisa que o previsto está incompleto")
    void forecastBeyondTheHorizon() throws Exception {
        earnings(today, today.plusDays(70), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("DAY"))
                .andExpect(jsonPath("$.forecastIncomplete").value(true));
    }

    @Test
    @DisplayName("período inválido ou agrupamento desconhecido: 422")
    void rejectsInvalidParameters() throws Exception {
        earnings(today, today.minusDays(1), "DAY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        earnings(today, today.plusDays(100), "DAY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        earnings(today, today.plusDays(6), "MONTH")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_BY"));
    }

    @Test
    @DisplayName("cada psicóloga só soma as próprias sessões")
    void otherAccountSeesNothing() throws Exception {
        String maria = createPatient("Maria da Ana");
        String paid = session(maria, -1);
        pay(paid);
        action(session(maria, -2), "complete");

        psychologists.as(bia);
        earnings(today.minusDays(7), today, "DAY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(0.0))
                .andExpect(jsonPath("$.receivableSessions").isEmpty());

        psychologists.as(ana);
        earnings(today.minusDays(7), today, "DAY")
                .andExpect(jsonPath("$.totals.received").value(180.0))
                .andExpect(jsonPath("$.totals.receivable").value(180.0));
    }
}
