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
import static org.mockito.Mockito.times;
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

        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
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
    void structuredReportIsParsedAndCleaned() throws Exception {
        when(assistant.complete(any())).thenReturn(new AiCompletion("""
                ```json
                {"summary":"Hay 4 hallazgos <br> críticos.","impact":"Riesgo de doble cobertura.","riskLevel":"high",
                 "priorities":[{"title":"Pólizas solapadas","severity":"RED","recordType":"Póliza","count":2,
                   "risk":"Pagos duplicados","action":"Cancelar la póliza más reciente"}],
                 "actions":[{"title":"Revisar pólizas","owner":"Afiliación","detail":"Confirmar con la aseguradora"}],
                 "prevention":["Validar fechas al emitir"]}
                ```""", "openai/gpt-oss-120b", 300, 200, 1200));

        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.summary").value("Hay 4 hallazgos críticos."))
                .andExpect(jsonPath("$.report.riskLevel").value("ALTO"))
                .andExpect(jsonPath("$.report.priorities[0].severity").value("Crítica"))
                .andExpect(jsonPath("$.report.priorities[0].count").value(2))
                .andExpect(jsonPath("$.report.actions[0].owner").value("Afiliación"))
                .andExpect(jsonPath("$.report.prevention[0]").value("Validar fechas al emitir"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.startsWith("## Resumen\nHay 4 hallazgos críticos.")));

        ArgumentCaptor<AiPrompt> prompt = ArgumentCaptor.forClass(AiPrompt.class);
        verify(assistant).complete(prompt.capture());
        assertThat(prompt.getValue().structured()).isTrue();
    }

    @Test
    void invalidJsonFallsBackToRawText() throws Exception {
        when(assistant.complete(any())).thenReturn(new AiCompletion("Texto libre sin JSON", "m", 1, 1, 1));
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content").value("Texto libre sin JSON"))
                .andExpect(jsonPath("$.report").doesNotExist());
    }

    @Test
    void repeatedInsightsAreServedFromCacheUntilRefresh() throws Exception {
        when(assistant.complete(any())).thenReturn(new AiCompletion("Primera", "grok-4.7", 10, 5, 100));
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.cached").value(false));

        when(assistant.complete(any())).thenReturn(new AiCompletion("Segunda", "grok-4.7", 10, 5, 100));
        mvc.perform(post("/api/audit/ai/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content").value("Primera"))
                .andExpect(jsonPath("$.cached").value(true));
        verify(assistant, times(1)).complete(any());

        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content").value("Segunda"))
                .andExpect(jsonPath("$.cached").value(false));
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
                AiUnavailableException.Reason.NOT_CONFIGURED, "El análisis con IA no está configurado: defina AI_API_KEY"));
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.detail").value("El análisis con IA no está configurado: defina AI_API_KEY"));

        doThrow(new AiUnavailableException(AiUnavailableException.Reason.RATE_LIMITED, "Límite"))
                .when(assistant).complete(any());
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));

        doThrow(new AiUnavailableException(AiUnavailableException.Reason.NO_CREDITS, "Sin créditos"))
                .when(assistant).complete(any());
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("NO_CREDITS"))
                .andExpect(jsonPath("$.detail").value("Sin créditos"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/audit/ai/insights").param("refresh", "true")).andExpect(status().isUnauthorized());
    }
}
