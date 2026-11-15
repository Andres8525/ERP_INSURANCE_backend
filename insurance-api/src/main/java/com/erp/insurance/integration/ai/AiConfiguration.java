package com.erp.insurance.integration.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    AiAssistant aiAssistant(AiProperties properties, RestClient.Builder builder) {
        if (!properties.configured()) {
            log.info("Análisis con IA desactivado: AI_API_KEY no está definida");
            return new DisabledAiAssistant(properties);
        }
        log.info("Análisis con IA activo: proveedor={} modelo={} url={}", properties.resolvedProvider().displayName(),
                properties.resolvedModel(), properties.resolvedBaseUrl());
        return new OpenAiCompatibleAssistant(properties, builder);
    }
}
