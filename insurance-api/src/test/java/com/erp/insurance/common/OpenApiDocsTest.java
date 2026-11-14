package com.erp.insurance.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La especificación OpenAPI es pública y documenta la seguridad Bearer y los módulos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class OpenApiDocsTest {
    @Autowired MockMvc mvc;

    @Test
    void specIsPublicAndDescribesSecurity() throws Exception {
        mvc.perform(get("/api/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ERP Insurance API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/audit/resolve-batch'].post.summary").value("Resolver anomalías en lote"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.tags[?(@.name == 'Siniestros')]").exists());
    }
}
