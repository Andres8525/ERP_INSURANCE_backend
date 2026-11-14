package com.erp.insurance.audit;

import com.erp.insurance.demo.DemoDataSeeder;
import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import com.erp.insurance.security.EmailLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AnomalyScanTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PatientRepository patients;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = tokenFor(DemoDataSeeder.DEMO_ADMIN, DemoDataSeeder.DEMO_PASSWORD);
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private PatientEntity legacyPatient(String email, String phone) {
        PatientEntity patient = new PatientEntity();
        patient.setFirstName("Piedad Del Socorro");
        patient.setLastName("Garcia Nupan");
        patient.setEmail(email);
        patient.setEmailLookupHash(EmailLookup.hash(email));
        patient.setPhone(phone);
        patient.setDateOfBirth("1969-03-10");
        patient.setAcaEligibilityStatus(AcaEligibilityStatus.UNKNOWN);
        patient.setAccountStatus(AccountStatus.PENDING);
        return patients.save(patient);
    }

    private JsonNode scan() throws Exception {
        String body = mvc.perform(post("/api/audit/scan").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void apiRejectsEmailsWithoutDomainDot() throws Exception {
        mvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Piedad","lastName":"Garcia","email":"piedad@gmailcom",
                                 "dateOfBirth":"1969-03-10"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void scanDetectsLegacyDataOnceAndAutoCorrectionFixesIt() throws Exception {
        PatientEntity patient = legacyPatient("piedad@gmailcom", "3054508353");

        JsonNode first = scan();
        assertThat(first.get("engineMode").asText()).isEqualTo("IN_PROCESS");
        assertThat(first.get("engineVersion").asText()).startsWith("anomaly-engine/");
        assertThat(first.get("created").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(first.get("analyzedRecords").asInt()).isGreaterThan(1);

        JsonNode second = scan();
        assertThat(second.get("created").asInt()).isZero();
        assertThat(second.get("alreadyKnown").asInt()).isEqualTo(second.get("findings").asInt());

        String page = mvc.perform(get("/api/audit/anomalies").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID emailAnomaly = null;
        for (JsonNode anomaly : json.readTree(page).get("content")) {
            if (patient.getId().toString().equals(anomaly.path("affectedRecordId").asText())
                    && "email".equals(anomaly.path("suggestedCorrectionField").asText())) {
                assertThat(anomaly.get("suggestedCorrectionValue").asText()).isEqualTo("piedad@gmail.com");
                assertThat(anomaly.get("autoCorrectable").asBoolean()).isTrue();
                emailAnomaly = UUID.fromString(anomaly.get("id").asText());
            }
        }
        assertThat(emailAnomaly).isNotNull();

        mvc.perform(post("/api/audit/resolve-batch").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("ids", new UUID[] {emailAnomaly},
                                "action", "AUTOCORREGIR"))))
                .andExpect(status().isOk());
        assertThat(patients.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo("piedad@gmail.com");
    }

    @Test
    void agentsCannotRunTheScan() throws Exception {
        String email = "agente." + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "ClaveSegura2026!"))))
                .andExpect(status().isCreated());
        String agent = tokenFor(email, "ClaveSegura2026!");

        mvc.perform(post("/api/audit/scan").header(HttpHeaders.AUTHORIZATION, "Bearer " + agent))
                .andExpect(status().isForbidden());
    }
}
