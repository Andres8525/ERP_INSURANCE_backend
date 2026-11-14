package com.erp.insurance.audit;

import com.erp.insurance.demo.DemoDataSeeder;
import com.erp.insurance.integration.ai.AiAssistant;
import com.erp.insurance.integration.ai.AiAssistant.AiCompletion;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import com.erp.insurance.integration.ai.AiUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AiInsightApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataAnomalyRepository anomalies;
    @MockitoBean AiAssistant assistant;

    private String token;

    @BeforeEach
    void login() throws Exception {
        when(assistant.provider()).thenReturn("grok");
        when(assistant.model()).thenReturn("grok-4.7");
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_ADMIN,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    @Test
    void reportsStatus() throws Exception {
        when(assistant.available()).thenReturn(false);
        mvc.perform(get("/api/audit/ai/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.provider").value("grok"))
                .andExpect(jsonPath("$.model").value("grok-4.7"));
    }

    @Test
    void insightsSendOnlyAnonymizedMetadata() throws Exception {
        when(assistant.complete(any())).thenReturn(new AiCompletion("## Diagnóstico\nOK", "grok-4.7", 200, 80, 900));

        mvc.perform(post("/api/audit/ai/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("## Diagnóstico\nOK"))
                .andExpect(jsonPath("$.analyzedAnomalies").isNumber())
                .andExpect(jsonPath("$.completionTokens").value(80));

        ArgumentCaptor<AiPrompt> prompt = ArgumentCaptor.forClass(AiPrompt.class);
        verify(assistant).complete(prompt.capture());
        assertThat(prompt.getValue().user())
                .contains("anomalías abiertas")
                .doesNotContain("Pérez", "Lucía", "@example.test", "exmaple", "CRMID-", "305-555", "38400");
    }

    @Test
    void explainsOneAnomaly() throws Exception {
        UUID id = anomalies.findAll().getFirst().getId();
        when(assistant.complete(any())).thenReturn(new AiCompletion("Corrija el campo", "grok-4.7", 50, 20, 300));

        mvc.perform(post("/api/audit/anomalies/{id}/ai-explanation", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Corrija el campo"))
                .andExpect(jsonPath("$.analyzedAnomalies").value(1));

        mvc.perform(post("/api/audit/anomalies/{id}/ai-explanation", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsProviderFailuresToHttpStatus() throws Exception {
        when(assistant.complete(any())).thenThrow(new AiUnavailableException(
                AiUnavailableException.Reason.NOT_CONFIGURED, "El análisis con IA no está configurado: defina GROK_API_KEY"));
        mvc.perform(post("/api/audit/ai/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        doThrow(new AiUnavailableException(AiUnavailableException.Reason.RATE_LIMITED, "Límite"))
                .when(assistant).complete(any());
        mvc.perform(post("/api/audit/ai/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/audit/ai/insights")).andExpect(status().isUnauthorized());
    }
}
