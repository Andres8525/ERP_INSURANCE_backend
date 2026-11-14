package com.erp.insurance.integration.ai;

public interface AiAssistant {
    boolean available();

    String provider();

    String model();

    AiCompletion complete(AiPrompt prompt);

    record AiPrompt(String system, String user) {}

    record AiCompletion(String content, String model, int promptTokens, int completionTokens, long latencyMs) {}
}
