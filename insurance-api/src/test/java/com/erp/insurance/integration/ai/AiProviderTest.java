package com.erp.insurance.integration.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderTest {
    private static AiProperties properties(String provider, String key, String baseUrl, String model) {
        return new AiProperties(provider, key, baseUrl, model, Duration.ofSeconds(5), Duration.ofSeconds(60),
                0.2, 900, 200, Duration.ofMinutes(10), null);
    }

    @Test
    void detectsProviderFromKeyPrefix() {
        AiProperties groq = properties("auto", "gsk_abc", null, null);
        assertThat(groq.resolvedProvider()).isEqualTo(AiProvider.GROQ);
        assertThat(groq.resolvedBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(groq.resolvedModel()).isEqualTo("openai/gpt-oss-120b");

        assertThat(properties("auto", "xai-abc", "", "").resolvedProvider()).isEqualTo(AiProvider.GROK);
        assertThat(properties(null, "AIzaSy123", null, null).resolvedModel()).isEqualTo("gemini-2.5-flash");
        assertThat(properties("auto", "sk-or-v1-abc", null, null).resolvedBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    }

    @Test
    void explicitSettingsWinOverDetection() {
        AiProperties custom = properties("GROQ", "xai-abc", " https://proxy.local/v1 ", "qwen/qwen3.8-27b");
        assertThat(custom.resolvedProvider()).isEqualTo(AiProvider.GROQ);
        assertThat(custom.resolvedBaseUrl()).isEqualTo("https://proxy.local/v1");
        assertThat(custom.resolvedModel()).isEqualTo("qwen/qwen3.8-27b");
        assertThat(properties("auto", "unknown-key", null, null).resolvedProvider()).isEqualTo(AiProvider.GROQ);
    }

    @Test
    void asksGptOssOnGroqForShortReasoning() {
        assertThat(properties("auto", "gsk_abc", null, null).resolvedReasoningEffort()).isEqualTo("low");
        assertThat(properties("auto", "gsk_abc", null, "qwen/qwen3.8-27b").resolvedReasoningEffort()).isNull();
        assertThat(properties("auto", "xai-abc", null, null).resolvedReasoningEffort()).isNull();
        AiProperties none = new AiProperties("auto", "gsk_abc", null, null, Duration.ofSeconds(5), Duration.ofSeconds(60),
                0.2, 900, 200, Duration.ofMinutes(10), "none");
        assertThat(none.resolvedReasoningEffort()).isNull();
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> properties("chatgpt", "k", null, null).resolvedProvider())
                .hasMessageContaining("AI_PROVIDER desconocido");
    }
}
