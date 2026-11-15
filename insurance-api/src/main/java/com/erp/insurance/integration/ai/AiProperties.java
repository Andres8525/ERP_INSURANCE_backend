package com.erp.insurance.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("auto") String provider,
                           String apiKey,
                           String baseUrl,
                           String model,
                           @DefaultValue("5s") Duration connectTimeout,
                           @DefaultValue("60s") Duration readTimeout,
                           @DefaultValue("0.2") double temperature,
                           @DefaultValue("4000") int maxTokens,
                           @DefaultValue("200") int maxAnomalies,
                           @DefaultValue("10m") Duration cacheTtl,
                           String reasoningEffort) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiProvider resolvedProvider() {
        boolean auto = provider == null || provider.isBlank() || provider.equalsIgnoreCase("auto");
        if (!auto) {
            return AiProvider.fromId(provider).orElseThrow(() -> new IllegalStateException(
                    "AI_PROVIDER desconocido: " + provider + " (use auto, groq, grok, gemini u openrouter)"));
        }
        return AiProvider.fromApiKey(apiKey).orElse(AiProvider.GROQ);
    }

    public String resolvedBaseUrl() {
        return hasText(baseUrl) ? baseUrl.strip() : resolvedProvider().baseUrl();
    }

    public String resolvedModel() {
        return hasText(model) ? model.strip() : resolvedProvider().defaultModel();
    }

    public String resolvedReasoningEffort() {
        if (hasText(reasoningEffort)) {
            return reasoningEffort.strip().equalsIgnoreCase("none") ? null : reasoningEffort.strip().toLowerCase();
        }
        return resolvedProvider() == AiProvider.GROQ && resolvedModel().startsWith("openai/gpt-oss") ? "low" : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
