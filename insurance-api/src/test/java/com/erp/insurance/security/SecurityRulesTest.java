package com.erp.insurance.security;

import com.erp.insurance.demo.DemoDataSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class SecurityRulesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String email, String password, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenFor(String email) throws Exception {
        return json.readTree(login(email, DemoDataSeeder.DEMO_PASSWORD, 200)).get("accessToken").asText();
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        login(DemoDataSeeder.DEMO_ADMIN, "contraseña-incorrecta", 401);
        login("nadie@nexo.demo", DemoDataSeeder.DEMO_PASSWORD, 401);
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = tokenFor(DemoDataSeeder.DEMO_ADMIN);
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        mvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentCannotAccessAuditButCanUseClaims() throws Exception {
        String token = tokenFor("agente@nexo.demo");
        mvc.perform(get("/api/audit/anomalies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/claims/pipeline").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void auditorCanAccessAudit() throws Exception {
        String token = tokenFor("auditor@nexo.demo");
        mvc.perform(get("/api/audit/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void meReturnsCurrentSession() throws Exception {
        String token = tokenFor("auditor@nexo.demo");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auditor@nexo.demo"))
                .andExpect(jsonPath("$.role").value("AUDITOR"))
                .andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
