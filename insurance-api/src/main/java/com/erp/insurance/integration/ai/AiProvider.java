package com.erp.insurance.integration.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProvider {
    GROQ("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", "gsk_", "console.groq.com"),
    GROK("Grok (xAI)", "https://api.x.ai/v1", "grok-4.7", "xai-", "console.x.ai"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "AIza", "aistudio.google.com"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b:free", "sk-or-", "openrouter.ai");

    private final String displayName;
    private final String baseUrl;
    private final String defaultModel;
    private final String keyPrefix;
    private final String console;

    AiProvider(String displayName, String baseUrl, String defaultModel, String keyPrefix, String console) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.keyPrefix = keyPrefix;
        this.console = console;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String console() {
        return console;
    }

    public static Optional<AiProvider> fromId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(provider -> provider.name().equals(normalized)).findFirst();
    }

    public static Optional<AiProvider> fromApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(provider -> apiKey.strip().startsWith(provider.keyPrefix)).findFirst();
    }
}
