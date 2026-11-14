package com.erp.insurance.common;

import com.erp.insurance.demo.DemoDataSeeder;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ErrorHandlingTest {
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

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void invalidBodyReportsFieldErrors() throws Exception {
        mvc.perform(auth(post("/api/patients")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"X\",\"email\":\"no-es-email\",\"dateOfBirth\":\"12/04/1990\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    void outOfRangeQueryParamIsBadRequest() throws Exception {
        mvc.perform(auth(get("/api/patients").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void invalidEnumListsAllowedValues() throws Exception {
        mvc.perform(auth(post("/api/claims")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"00000000-0000-0000-0000-000000000000\",\"description\":\"x\",\"priority\":\"URGENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("NORMAL")));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        mvc.perform(auth(post("/api/audit/resolve-batch")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"action\":\"ARCHIVAR_DEFINITIVO\",\"force\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("force")));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(auth(post("/api/patients")).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }
}
