package com.erp.insurance.auth;

import com.erp.insurance.domain.DomainEnums.UserRole;
import com.erp.insurance.security.AdaptiveBCryptPasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los hashes con un coste BCrypt distinto se re-cifran tras un login correcto. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class PasswordRehashTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @Test
    void legacyCost12HashIsDowngradedToConfiguredCostOnLogin() throws Exception {
        String email = "legacy." + UUID.randomUUID() + "@example.test";
        String password = "ClaveHeredada2026!";
        users.save(new UserEntity(email, new BCryptPasswordEncoder(12).encode(password), UserRole.AGENT));
        assertThat(users.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow().getPasswordHash()).startsWith("$2a$12$");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());

        String rehashed = users.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow().getPasswordHash();
        assertThat(rehashed).startsWith("$2a$10$");
        assertThat(new BCryptPasswordEncoder().matches(password, rehashed)).isTrue();

        // El nuevo hash sigue funcionando para iniciar sesión.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordDoesNotTouchTheHash() throws Exception {
        String email = "legacy." + UUID.randomUUID() + "@example.test";
        String original = new BCryptPasswordEncoder(12).encode("ClaveHeredada2026!");
        users.save(new UserEntity(email, original, UserRole.AGENT));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "ClaveIncorrecta2026"))))
                .andExpect(status().isUnauthorized());

        assertThat(users.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow().getPasswordHash()).isEqualTo(original);
    }

    @Test
    void encoderOnlyUpgradesHashesWithADifferentCost() {
        AdaptiveBCryptPasswordEncoder encoder = new AdaptiveBCryptPasswordEncoder(10);
        assertThat(encoder.upgradeEncoding(new BCryptPasswordEncoder(12).encode("x"))).isTrue();
        assertThat(encoder.upgradeEncoding(new BCryptPasswordEncoder(8).encode("x"))).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode("x"))).isFalse();
        assertThat(encoder.upgradeEncoding("texto-plano")).isFalse();
    }
}
