package com.erp.insurance.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("grok") String provider,
                           String apiKey,
                           @DefaultValue("https://api.x.ai/v1") String baseUrl,
                           @DefaultValue("grok-4.7") String model,
                           @DefaultValue("5s") Duration connectTimeout,
                           @DefaultValue("60s") Duration readTimeout,
                           @DefaultValue("0.2") double temperature,
                           @DefaultValue("900") int maxTokens,
                           @DefaultValue("200") int maxAnomalies,
                           @DefaultValue("10m") Duration cacheTtl) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
