package com.erp.insurance.clients;

import com.erp.insurance.demo.DemoDataSeeder;
import com.erp.insurance.patients.CrmIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClientsApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "agente@nexo.demo",
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(auth(request)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode clientNamed(JsonNode clients, String fullName) {
        for (JsonNode client : clients) {
            if (client.get("fullName").asText().equals(fullName)) return client;
        }
        throw new AssertionError("Client not found: " + fullName);
    }

    @Test
    void listsEveryClientWithCrmIdPlanCityAndDescription() throws Exception {
        JsonNode clients = read(get("/api/clients"));

        assertThat(clients.size()).isGreaterThanOrEqualTo(9);
        assertThat(clients).allSatisfy(client -> assertThat(CrmIds.isValid(client.get("crmId").asText())).isTrue());

        JsonNode ana = clientNamed(clients, "Ana Pérez");
        assertThat(ana.get("city").asText()).isEqualTo("Miami");
        assertThat(ana.at("/currentPlan/zeroPremium").asBoolean()).isTrue();
        assertThat(ana.at("/currentPlan/insurer").asText()).isEqualTo("Oscar Health");
        assertThat(ana.get("description").asText())
                .isEqualTo("Simple Silver EPO de Oscar Health, prima $0 por subsidio ACA · Miami · Elegible ACA");

        JsonNode maria = clientNamed(clients, "María González");
        assertThat(maria.at("/currentPlan/status").asText()).isEqualTo("ACTIVE");
        assertThat(maria.get("description").asText()).contains("prima $428.16/mes");
    }

    @Test
    void searchMatchesCrmIdCityAndInsurer() throws Exception {
        JsonNode tampa = read(get("/api/clients").param("q", "tampa"));
        assertThat(tampa).isNotEmpty().allSatisfy(client -> assertThat(client.get("city").asText()).isEqualTo("Tampa"));

        String crmId = read(get("/api/clients")).get(0).get("crmId").asText();
        JsonNode byCrm = read(get("/api/clients").param("q", crmId.toLowerCase()));
        assertThat(byCrm.size()).isEqualTo(1);

        assertThat(read(get("/api/clients").param("q", "aetna")).get(0).get("fullName").asText()).isEqualTo("David Williams");
    }

    @Test
    void newClientsGetCityAndCrmIdAndCanHaveZeroPremiumPlans() throws Exception {
        JsonNode patient = json.readTree(mvc.perform(auth(post("/api/patients")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Piedad","lastName":"Garcia","email":"piedad.%s@gmail.com",
                                 "phone":"(305) 450-8353","city":"Boston","dateOfBirth":"1969-03-10"}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(patient.get("city").asText()).isEqualTo("Boston");
        assertThat(CrmIds.isValid(patient.get("crmId").asText())).isTrue();

        int year = LocalDate.now().getYear();
        mvc.perform(auth(post("/api/patients/" + patient.get("id").asText() + "/policies"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("insurer", "Ambetter", "planName", "Bronze ACA",
                                "coverageStart", year + "-12-01", "coverageEnd", year + "-12-31",
                                "premiumAmount", 0))))
                .andExpect(status().isCreated());

        JsonNode client = read(get("/api/clients/" + patient.get("id").asText()));
        assertThat(client.at("/currentPlan/zeroPremium").asBoolean()).isTrue();
        assertThat(client.get("description").asText()).contains("prima $0 por subsidio ACA · Boston");
    }

    @Test
    void downloadsTheClientRecordAsPdf() throws Exception {
        JsonNode ana = clientNamed(read(get("/api/clients")), "Ana Pérez");
        MockHttpServletResponse response = mvc.perform(auth(get("/api/clients/" + ana.get("id").asText() + "/pdf")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("cliente-" + ana.get("crmId").asText() + ".pdf");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        byte[] pdf = response.getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);
    }

    @Test
    void previewServesThePdfInlineAndRejectsUnknownDispositions() throws Exception {
        String id = read(get("/api/clients")).get(0).get("id").asText();
        MockHttpServletResponse inline = mvc.perform(auth(get("/api/clients/" + id + "/pdf").param("disposition", "inline")))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(inline.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");

        mvc.perform(auth(get("/api/clients/" + id + "/pdf").param("disposition", "script")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownClientsAreNotFoundAndAnonymousCallsAreRejected() throws Exception {
        mvc.perform(auth(get("/api/clients/" + UUID.randomUUID() + "/pdf"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }
}
