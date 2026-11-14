package com.erp.insurance.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CrossOriginHeadersTest {
    @Autowired MockMvc mvc;

    @Test
    void responsesCanBeEmbeddedByACrossOriginIsolatedFrontend() throws Exception {
        mvc.perform(get("/api/catalogs/icd10").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cross-Origin-Resource-Policy", "cross-origin"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("ETag")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Idempotent-Replayed")));
    }
}
