package com.erp.insurance.integration.ai;

public interface AiAssistant {
    boolean available();

    String provider();

    String model();

    AiCompletion complete(AiPrompt prompt);

    record AiPrompt(String system, String user, boolean structured) {
        public AiPrompt(String system, String user) {
            this(system, user, false);
        }
    }

    record AiCompletion(String content, String model, int promptTokens, int completionTokens, long latencyMs) {}
}
