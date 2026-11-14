package com.erp.insurance.auth;

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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class RegistrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String body(String email, String password) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    @Test
    void registeredUserCanUseTheApiAndLogInAgain() throws Exception {
        String email = "Nuevo." + UUID.randomUUID() + "@Example.test";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "ClaveSegura2026!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("accessToken").asText();

        mvc.perform(get("/api/claims/pipeline").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit/anomalies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email.toLowerCase(), "ClaveSegura2026!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADMIN@nexo.demo", "OtraClaveSegura1")))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndInvalidEmailAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("no-es-un-email", "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
