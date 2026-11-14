package com.erp.insurance;

import com.erp.insurance.demo.DemoDataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiIntegrationTest {
    private static final String FRONTEND = "http://localhost:4200";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_ADMIN,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header(HttpHeaders.ORIGIN, FRONTEND);
    }

    @Test
    void corsPreflightAllowsAngularOrigin() throws Exception {
        mvc.perform(options("/api/audit/resolve-batch")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
        mvc.perform(options("/api/claims/x").header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/audit/anomalies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void anomaliesInboxAndBatchResolution() throws Exception {
        String body = mvc.perform(auth(get("/api/audit/anomalies").param("resolutionStatus", "OPEN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = json.readTree(body).get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(5);
        assertThat(content.get(0).get("patient").get("fullName").asText()).isNotBlank();

        List<String> archive = new ArrayList<>();
        String autoCorrect = null;
        for (JsonNode anomaly : content) {
            if (anomaly.get("autoCorrectable").asBoolean() && autoCorrect == null) autoCorrect = anomaly.get("id").asText();
            else if (archive.size() < 2) archive.add(anomaly.get("id").asText());
        }
        assertThat(autoCorrect).isNotNull();

        mvc.perform(auth(post("/api/audit/resolve-batch")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("ids", archive, "action", "ARCHIVAR_DEFINITIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2));
        mvc.perform(auth(post("/api/audit/resolve-batch")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("ids", List.of(autoCorrect), "action", "AUTOCORREGIR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));

        mvc.perform(auth(get("/api/audit/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(content.size() - 3));
    }

    @Test
    void patientCrud() throws Exception {
        String created = mvc.perform(auth(post("/api/patients")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Carla","lastName":"Ríos","email":"carla.rios@example.test",
                                 "phone":"(305) 555-0101","dateOfBirth":"1990-04-12","householdIncome":42000.00,
                                 "acaEligibilityStatus":"PENDING","accountStatus":"PENDING"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mvc.perform(auth(post("/api/patients/" + id + "/policies")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"insurer":"Ambetter","planName":"Clear Silver 73","coverageStart":"2026-01-01",
                                 "coverageEnd":"2026-12-31","premiumAmount":428.16}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(auth(put("/api/patients/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.firstName").value("Carla"));

        mvc.perform(auth(get("/api/patients/" + id + "/policies"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(auth(delete("/api/patients/" + id))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/patients/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void claimCrudAndPipeline() throws Exception {
        String patients = mvc.perform(auth(get("/api/patients"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String patientId = json.readTree(patients).get("content").get(0).get("id").asText();

        String created = mvc.perform(auth(post("/api/claims")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("patientId", patientId, "description", "Consulta",
                                "priority", "HIGH", "location", "Miami",
                                "services", List.of(Map.of("name", "Transporte médico"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.services[0].name").value("Transporte médico"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mvc.perform(auth(patch("/api/claims/" + id + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(auth(put("/api/claims/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.00,\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(auth(get("/api/claims/pipeline")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.OPEN").isArray())
                .andExpect(jsonPath("$.IN_REVIEW").isArray())
                .andExpect(jsonPath("$.APPROVED").isArray())
                .andExpect(jsonPath("$.REJECTED").isArray());

        mvc.perform(auth(delete("/api/claims/" + id))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/claims/" + id))).andExpect(status().isNotFound());
    }
}
