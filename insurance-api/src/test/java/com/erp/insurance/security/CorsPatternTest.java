package com.erp.insurance.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS con patrones, como en un despliegue Render + Vercel. */
@SpringBootTest(properties = "app.cors.allowed-origins=https://erp-insurance.vercel.app,https://*.vercel.app")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CorsPatternTest {
    @Autowired MockMvc mvc;

    private void preflight(String origin, boolean allowed) throws Exception {
        var result = mvc.perform(options("/api/patients")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"));
        if (allowed) {
            result.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        } else {
            result.andExpect(status().isForbidden());
        }
    }

    @Test
    void allowsProductionAndPreviewDeployments() throws Exception {
        preflight("https://erp-insurance.vercel.app", true);
        preflight("https://erp-insurance-git-main-edison.vercel.app", true);
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        preflight("https://vercel.app.attacker.example", false);
        preflight("http://erp-insurance.vercel.app", false);
        preflight("http://localhost:4200", false);
    }
}
