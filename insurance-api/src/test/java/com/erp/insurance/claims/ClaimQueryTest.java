package com.erp.insurance.claims;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Filtros de GET /api/claims sobre los datos demo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ClaimQueryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_ADMIN,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    private JsonNode claims(String query) throws Exception {
        String body = mvc.perform(get("/api/claims" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void filtersByPriority() throws Exception {
        JsonNode high = claims("?priority=HIGH");
        assertThat(high.size()).isPositive();
        high.forEach(claim -> assertThat(claim.get("priority").asText()).isEqualTo("HIGH"));
        assertThat(high.size()).isLessThan(claims("").size());
    }

    @Test
    void combinesStatusAndPriority() throws Exception {
        JsonNode result = claims("?status=OPEN&priority=HIGH");
        result.forEach(claim -> {
            assertThat(claim.get("status").asText()).isEqualTo("OPEN");
            assertThat(claim.get("priority").asText()).isEqualTo("HIGH");
        });
    }

    @Test
    void filtersByPatient() throws Exception {
        JsonNode any = claims("").get(0);
        String patientId = any.get("patient").get("id").asText();
        JsonNode result = claims("?patientId=" + patientId);
        assertThat(result.size()).isPositive();
        result.forEach(claim -> assertThat(claim.get("patient").get("id").asText()).isEqualTo(patientId));
    }
}
