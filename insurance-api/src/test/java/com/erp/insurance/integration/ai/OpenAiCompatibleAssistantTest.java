package com.erp.insurance.integration.ai;

import com.erp.insurance.integration.ai.AiAssistant.AiCompletion;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleAssistantTest {
    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String errorBody = "{\"error\":\"x\"}";
    private volatile String responseBody = """
            {"id":"r1","object":"chat.completion","model":"grok-4.7","system_fingerprint":"fp",
             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"  ## Diagnóstico\\nTodo bien  ","refusal":null}}],
             "usage":{"prompt_tokens":120,"completion_tokens":45,"total_tokens":165}}""";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = (status == 200 ? responseBody : errorBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleAssistant assistant() {
        AiProperties properties = new AiProperties("grok", "xai-test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "grok-4.7",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 0.2, 300, 50, Duration.ofMinutes(10), null);
        return new OpenAiCompatibleAssistant(properties, RestClient.builder());
    }

    @Test
    void sendsOpenAiCompatibleRequestWithBearerKey() {
        AiCompletion completion = assistant().complete(new AiPrompt("sistema", "usuario"));

        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer xai-test-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"grok-4.7\"")
                .contains("\"max_tokens\":300")
                .contains("\"role\":\"system\"")
                .contains("\"content\":\"usuario\"")
                .doesNotContain("response_format", "reasoning_effort");
        assertThat(completion.content()).isEqualTo("## Diagnóstico\nTodo bien");
        assertThat(completion.model()).isEqualTo("grok-4.7");
        assertThat(completion.promptTokens()).isEqualTo(120);
        assertThat(completion.completionTokens()).isEqualTo(45);
    }

    @Test
    void requestsJsonModeForStructuredPrompts() {
        assistant().complete(new AiPrompt("sistema", "usuario", true));
        assertThat(requestBody.get()).contains("\"response_format\":{\"type\":\"json_object\"}");
    }

    @Test
    void translatesInvalidKeyAndRateLimit() {
        status = 401;
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .isInstanceOf(AiUnavailableException.class)
                .extracting(error -> ((AiUnavailableException) error).reason())
                .isEqualTo(AiUnavailableException.Reason.UNAUTHORIZED);

        status = 429;
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .extracting(error -> ((AiUnavailableException) error).reason())
                .isEqualTo(AiUnavailableException.Reason.RATE_LIMITED);
    }

    @Test
    void distinguishesMissingCreditsFromInvalidKey() {
        status = 403;
        errorBody = """
                {"code":"permission-denied","error":"Your team t1 has either used all available credits or reached its monthly spending limit."}""";
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .hasMessageContaining("créditos")
                .extracting(error -> ((AiUnavailableException) error).reason())
                .isEqualTo(AiUnavailableException.Reason.NO_CREDITS);

        errorBody = "{\"code\":\"permission-denied\",\"error\":\"Model not allowed for this API key\"}";
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .extracting(error -> ((AiUnavailableException) error).reason())
                .isEqualTo(AiUnavailableException.Reason.UNAUTHORIZED);
    }

    @Test
    void reportsUnknownModel() {
        status = 404;
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .hasMessageContaining("grok-4.7")
                .hasMessageContaining("AI_MODEL");
    }

    @Test
    void rejectsEmptyChoices() {
        responseBody = "{\"model\":\"grok-4.7\",\"choices\":[]}";
        assertThatThrownBy(() -> assistant().complete(new AiPrompt("s", "u")))
                .extracting(error -> ((AiUnavailableException) error).reason())
                .isEqualTo(AiUnavailableException.Reason.PROVIDER_ERROR);
    }

    @Test
    void disabledAssistantExplainsMissingKey() {
        AiProperties properties = new AiProperties("grok", "", "https://api.x.ai/v1", "grok-4.7",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0.2, 100, 10, Duration.ZERO, null);
        assertThat(properties.configured()).isFalse();
        DisabledAiAssistant disabled = new DisabledAiAssistant(properties);
        assertThat(disabled.available()).isFalse();
        assertThatThrownBy(() -> disabled.complete(new AiPrompt("s", "u")))
                .hasMessageContaining("AI_API_KEY");
    }
}
