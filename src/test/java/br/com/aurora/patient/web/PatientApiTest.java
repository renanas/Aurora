package br.com.aurora.patient.web;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.aurora.TestcontainersConfig;
import br.com.aurora.patient.api.PatientDeactivated;
import br.com.aurora.patient.api.PatientPriceChanged;
import br.com.aurora.shared.error.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** API completa contra PostgreSQL real: validação, busca sem acento, inativação, eventos. */
@SpringBootTest
@Import(TestcontainersConfig.class)
@RecordApplicationEvents
class PatientApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ApplicationEvents events;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).build();
        jdbc.update("DELETE FROM therapy_session");
        jdbc.update("DELETE FROM recurrence");
        jdbc.update("DELETE FROM patient");
    }

    // ---------- helpers ----------

    private static String json(String name, String whatsapp, String price) {
        return "{\"name\":\"" + name + "\",\"whatsapp\":\"" + whatsapp + "\",\"sessionPrice\":" + price + "}";
    }

    private String createPatient(String name) throws Exception {
        String body = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, "5511999998888", "180")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    // ---------- testes ----------

    @Test
    @DisplayName("cria paciente: 201, Location, nome aparado, WhatsApp só com dígitos, ativo")
    void createsPatient() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Maria Silva ", "+55 (11) 99999-8888", "180")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.whatsapp").value("5511999998888"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("dados inválidos: 400 VALIDATION_ERROR apontando nome, WhatsApp e valor")
    void rejectsInvalidData() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "12345", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "whatsapp", "sessionPrice")));
    }

    @Test
    @DisplayName("busca ignora acentos e maiúsculas: jose acha José, MAR acha Maria")
    void searchIgnoresAccentsAndCase() throws Exception {
        createPatient("José Almeida");
        createPatient("Maria Silva");
        createPatient("João Pereira");

        mvc.perform(get("/api/patients").param("search", "jose"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("José Almeida"));
        mvc.perform(get("/api/patients").param("search", "MAR"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Maria Silva"));
        mvc.perform(get("/api/patients").param("search", "joão"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("João Pereira"));
    }

    @Test
    @DisplayName("sem busca, lista todos os ativos em ordem alfabética sem considerar acento")
    void listsAllInAlphabeticalOrder() throws Exception {
        createPatient("Zélia");
        createPatient("André");
        createPatient("Beatriz");

        mvc.perform(get("/api/patients"))
                .andExpect(jsonPath("$[0].name").value("André"))
                .andExpect(jsonPath("$[1].name").value("Beatriz"))
                .andExpect(jsonPath("$[2].name").value("Zélia"));
    }

    @Test
    @DisplayName("% digitado na busca é texto, não curinga: não devolve todo mundo")
    void percentSignIsNotAWildcard() throws Exception {
        createPatient("Maria Silva");

        mvc.perform(get("/api/patients").param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("inativar: sai da lista de ativos, aparece em active=false e é idempotente")
    void deactivateMovesPatientToInactiveList() throws Exception {
        String id = createPatient("Maria Silva");

        mvc.perform(post("/api/patients/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/patients/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/patients")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/patients").param("active", "false"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("inativar publica PatientDeactivated uma única vez, mesmo repetindo a ação")
    void deactivatePublishesEventOnce() throws Exception {
        String id = createPatient("Maria Silva");

        mvc.perform(post("/api/patients/" + id + "/deactivate")).andExpect(status().isOk());
        mvc.perform(post("/api/patients/" + id + "/deactivate")).andExpect(status().isOk());

        assertThat(events.stream(PatientDeactivated.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("reativar devolve o paciente à lista de ativos")
    void activateRestoresPatient() throws Exception {
        String id = createPatient("Maria Silva");
        mvc.perform(post("/api/patients/" + id + "/deactivate")).andExpect(status().isOk());

        mvc.perform(post("/api/patients/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/patients")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("editar atualiza os dados; o evento de valor só sai quando o valor muda")
    void priceChangedEventOnlyWhenPriceChanges() throws Exception {
        String id = createPatient("Maria Silva");

        mvc.perform(put("/api/patients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Maria S. Santos", "5511999998888", "180.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria S. Santos"));
        assertThat(events.stream(PatientPriceChanged.class).count()).isZero();

        mvc.perform(put("/api/patients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Maria S. Santos", "5511999998888", "200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionPrice").value(200.0));
        assertThat(events.stream(PatientPriceChanged.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("id inexistente: 404 NOT_FOUND")
    void unknownIdReturns404() throws Exception {
        mvc.perform(get("/api/patients/3f2c1a6e-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
