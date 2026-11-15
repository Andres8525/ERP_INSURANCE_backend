package com.erp.insurance.retention;

import com.erp.insurance.demo.DemoDataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class RetentionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    @Test
    void profilesHistoricalRecordsIntoArchiveDeleteAndLegalHold() throws Exception {
        String body = mvc.perform(get("/api/audit/retention")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(DemoDataSeeder.DEMO_ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode profile = json.readTree(body);
        JsonNode summary = profile.get("summary");

        assertThat(summary.get("delete").asInt()).isGreaterThan(0);
        assertThat(summary.get("archive").asInt()).isGreaterThan(0);
        assertThat(summary.get("legalHold").asInt()).isGreaterThan(0);
        assertThat(summary.get("profiled").asInt()).isEqualTo(summary.get("keep").asInt() + summary.get("archive").asInt()
                + summary.get("delete").asInt() + summary.get("legalHold").asInt());
        assertThat(summary.get("monthlySavingsUsd").asDouble()).isPositive();
        assertThat(summary.get("optimizedMonthlyCostUsd").asDouble()).isLessThan(summary.get("monthlyCostUsd").asDouble());
        assertThat(profile.get("rules").get("retentionYears").asInt()).isEqualTo(7);
        assertThat(profile.get("categories")).hasSize(3);

        List<JsonNode> candidates = StreamSupport.stream(profile.get("candidates").spliterator(), false).toList();
        assertThat(candidates).isNotEmpty().noneMatch(candidate -> candidate.get("decision").asText().equals("KEEP"));
        assertThat(candidates.getFirst().get("decision").asText()).isEqualTo("DELETE");
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.get("decision").asText()).isEqualTo("LEGAL_HOLD");
            assertThat(candidate.get("reason").asText()).contains("en trámite");
        });
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.get("crmId").asText()).startsWith("CRMID-"));
    }

    @Test
    void agentsCannotSeeTheRetentionProfile() throws Exception {
        mvc.perform(get("/api/audit/retention").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("agente@nexo.demo")))
                .andExpect(status().isForbidden());
    }
}
