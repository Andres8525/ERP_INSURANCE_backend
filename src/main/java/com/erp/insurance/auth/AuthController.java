package com.erp.insurance.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.erp.insurance.domain.DomainEnums.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;

@RestController
@Tag(name = "Autenticación", description = "Login JWT y sesión actual")
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtEncoder jwtEncoder,
                          @Value("${app.security.jwt-ttl-minutes:15}") long ttlMinutes) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }

    @Operation(summary = "Iniciar sesión", description = "Valida email y contraseña y devuelve un JWT HS256 con el rol del usuario.")
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
            UserEntity user = users.findByEmailIgnoreCaseAndActiveTrue(authentication.getName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(ttlMinutes * 60);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("erp-insurance-api")
                    .issuedAt(now)
                    .expiresAt(expiry)
                    .subject(user.getId().toString())
                    .claim("email", user.getEmail())
                    .claim("role", user.getRole().name())
                    .build();
            return new TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue(), "Bearer", expiry,
                    user.getEmail(), user.getRole());
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
    }

    /** Datos de la sesión actual a partir del JWT (útil para validar un token guardado en el cliente). */
    @Operation(summary = "Sesión actual", description = "Email, rol y expiración del token enviado.")
    @GetMapping("/me")
    public SessionResponse me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        return new SessionResponse(jwt.getClaimAsString("email"), UserRole.valueOf(jwt.getClaimAsString("role")),
                jwt.getExpiresAt());
    }

    public record LoginRequest(@Email @NotBlank @Size(max = 320) String email,
                               @NotBlank @Size(min = 12, max = 128) String password) {}
    public record SessionResponse(String email, UserRole role, Instant expiresAt) {}
    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, String email, UserRole role) {}
}