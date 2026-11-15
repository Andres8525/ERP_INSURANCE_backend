package com.erp.insurance.integration.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.List;

public class OpenAiCompatibleAssistant implements AiAssistant {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAssistant.class);

    private final AiProperties properties;
    private final AiProvider provider;
    private final String model;
    private final RestClient http;

    public OpenAiCompatibleAssistant(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.provider = properties.resolvedProvider();
        this.model = properties.resolvedModel();
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        this.http = builder.clone()
                .baseUrl(properties.resolvedBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String provider() {
        return provider.id();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        ChatRequest request = new ChatRequest(model,
                List.of(new Message("system", prompt.system()), new Message("user", prompt.user())),
                properties.temperature(), properties.maxTokens());
        long startedAt = System.nanoTime();
        ChatResponse response;
        try {
            response = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException exception) {
            throw translate(exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.TIMEOUT,
                    provider.displayName() + " no respondió a tiempo", exception);
        }
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_ERROR, provider.displayName() + " devolvió una respuesta vacía");
        }
        Usage usage = response.usage() == null ? new Usage(0, 0) : response.usage();
        return new AiCompletion(response.choices().getFirst().message().content().strip(),
                response.model() == null ? model : response.model(),
                usage.promptTokens(), usage.completionTokens(), latencyMs);
    }

    private AiUnavailableException translate(HttpStatusCode status, RestClientResponseException exception) {
        log.warn("{} respondió HTTP {}", provider.displayName(), status.value());
        if (status.value() == 402 || (status.value() == 403 && mentionsBilling(exception.getResponseBodyAsString()))) {
            return new AiUnavailableException(AiUnavailableException.Reason.NO_CREDITS,
                    "La cuenta de " + provider.displayName() + " no tiene créditos o alcanzó su límite de gasto: revise " + provider.console(),
                    exception);
        }
        return switch (status.value()) {
            case 401, 403 -> new AiUnavailableException(AiUnavailableException.Reason.UNAUTHORIZED,
                    "La API key de " + provider.displayName() + " es inválida, fue revocada o no tiene permiso para el modelo " + model,
                    exception);
            case 429 -> new AiUnavailableException(AiUnavailableException.Reason.RATE_LIMITED,
                    "Límite de uso de " + provider.displayName() + " alcanzado, intente de nuevo en unos segundos", exception);
            case 404 -> new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_ERROR,
                    "El modelo " + model + " no existe en " + provider.displayName() + ": revise AI_MODEL", exception);
            default -> new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_ERROR,
                    provider.displayName() + " respondió con error HTTP " + status.value(), exception);
        };
    }

    static boolean mentionsBilling(String body) {
        if (body == null) return false;
        String text = body.toLowerCase(java.util.Locale.ROOT);
        return text.contains("credit") || text.contains("spending limit") || text.contains("billing");
    }

    record ChatRequest(String model, List<Message> messages, double temperature,
                       @JsonProperty("max_tokens") int maxTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(String model, List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") int promptTokens,
                 @JsonProperty("completion_tokens") int completionTokens) {}
}
