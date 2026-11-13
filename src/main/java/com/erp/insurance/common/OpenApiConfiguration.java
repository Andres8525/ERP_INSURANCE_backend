package com.erp.insurance.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI (Swagger UI en /api/docs). Declara el esquema Bearer JWT para que
 * el botón "Authorize" permita probar los endpoints protegidos con el token de /api/auth/login.
 */
@Configuration
public class OpenApiConfiguration {
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI erpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ERP Insurance API")
                        .version("1.0.0")
                        .description("API del ERP para corredoras de seguros médicos ACA: pacientes, pólizas, "
                                + "siniestros y auditoría de calidad de datos asistida por IA.")
                        .license(new License().name("Uso académico")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtenido en POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
