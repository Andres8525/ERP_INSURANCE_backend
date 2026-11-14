package com.erp.insurance.policies;

import com.erp.insurance.demo.DemoDataSeeder;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String token;
    private String patientId;

    @BeforeEach
    void setUp() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_ADMIN,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(login).get("accessToken").asText();
        String patient = mvc.perform(auth(post("/api/patients")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("firstName", "Test", "lastName", "Policy",
                                "email", "policy." + UUID.randomUUID() + "@example.test", "dateOfBirth", "1985-02-03"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        patientId = json.readTree(patient).get("id").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String createPolicy(String start, String end, int expectedStatus) throws Exception {
        return mvc.perform(auth(post("/api/patients/" + patientId + "/policies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("insurer", "Ambetter", "coverageStart", start,
                                "coverageEnd", end, "premiumAmount", 300))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void rejectsOverlappingCoverage() throws Exception {
        createPolicy("2027-01-01", "2027-12-31", 201);
        createPolicy("2027-06-01", "2028-05-31", 409);
        createPolicy("2028-01-01", "2028-12-31", 201);
    }

    @Test
    void rejectsEndBeforeStart() throws Exception {
        createPolicy("2027-12-31", "2027-01-01", 422);
    }

    @Test
    void updatesAndDeletesPolicy() throws Exception {
        String id = json.readTree(createPolicy("2027-01-01", "2027-12-31", 201)).get("id").asText();
        String url = "/api/patients/" + patientId + "/policies/" + id;

        mvc.perform(auth(put(url)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"premiumAmount\":412.50,\"status\":\"ACTIVE\",\"planName\":\"Gold\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premiumAmount").value(412.50))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planName").value("Gold"));

        mvc.perform(auth(delete(url))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/patients/" + patientId + "/policies"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void policyOfAnotherPatientIsNotFound() throws Exception {
        String id = json.readTree(createPolicy("2027-01-01", "2027-12-31", 201)).get("id").asText();
        mvc.perform(auth(delete("/api/patients/" + UUID.randomUUID() + "/policies/" + id)))
                .andExpect(status().isNotFound());
    }
}
