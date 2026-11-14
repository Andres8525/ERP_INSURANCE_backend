package com.erp.insurance.analytics;

import com.erp.insurance.demo.DemoDataSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AnalyticsApiTest {
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

    private MockHttpServletResponse csv(String query) throws Exception {
        MvcResult started = mvc.perform(get("/api/analytics/billing-records.csv" + query)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse();
    }

    @Test
    void streamsDeterministicBillingRecordsAsCsv() throws Exception {
        MockHttpServletResponse first = csv("?count=500&seed=7");
        String body = first.getContentAsString();
        String[] lines = body.split("\n");

        assertThat(first.getContentType()).startsWith("text/csv");
        assertThat(lines[0]).isEqualTo(BillingRecordGenerator.HEADER);
        assertThat(lines).hasSize(501);
        assertThat(lines[1].split(",")).hasSize(10);
        assertThat(csv("?count=500&seed=7").getContentAsString()).isEqualTo(body);
        assertThat(csv("?count=500&seed=8").getContentAsString()).isNotEqualTo(body);
    }

    @Test
    void rejectsOversizedRequestsAndAnonymousCalls() throws Exception {
        mvc.perform(get("/api/analytics/billing-records.csv").param("count", "200001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics/billing-records.csv")).andExpect(status().isUnauthorized());
    }
}
