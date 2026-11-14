package com.erp.insurance.common;

import com.erp.insurance.demo.DemoDataSeeder;
import com.erp.insurance.patients.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdempotencyApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PatientRepository patients;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_ADMIN,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletResponse createPatient(String key, String email) throws Exception {
        return mvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Offline\",\"lastName\":\"Queue\",\"email\":\"" + email
                                + "\",\"dateOfBirth\":\"1980-01-01\"}"))
                .andReturn().getResponse();
    }

    @Test
    void replayingTheSameKeyReturnsTheOriginalResponseWithoutDuplicating() throws Exception {
        String key = "sync-" + UUID.randomUUID();
        String email = "offline." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        long before = patients.count();

        MockHttpServletResponse first = createPatient(key, email);
        MockHttpServletResponse replay = createPatient(key, email);

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(patients.count()).isEqualTo(before + 1);

        MockHttpServletResponse otherKey = createPatient("sync-" + UUID.randomUUID(), email);
        assertThat(otherKey.getStatus()).isEqualTo(409);
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        assertThat(createPatient("short", "x@example.test").getStatus()).isEqualTo(400);
    }

    @Test
    void corsAllowsTheHeaderAndExposesTheReplayMarker() throws Exception {
        mvc.perform(options("/api/patients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }

    @Test
    void entriesExpireAfterTheirTtlAndTheStoreIsBounded() throws Exception {
        MutableClock clock = new MutableClock();
        IdempotencyFilter filter = new IdempotencyFilter(Duration.ofMinutes(1), 2, clock);
        for (String key : new String[] {"key-00001", "key-00002", "key-00003"}) send(filter, key);
        assertThat(filter.size()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(2));
        send(filter, "key-00004");
        assertThat(filter.size()).isEqualTo(1);
    }

    private static void send(IdempotencyFilter filter, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/claims");
        request.addHeader(IdempotencyFilter.HEADER, key);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                res.setStatus(201);
            }
        }));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-14T12:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
