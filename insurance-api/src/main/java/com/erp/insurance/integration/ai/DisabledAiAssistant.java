package com.erp.insurance.integration.ai;

public class DisabledAiAssistant implements AiAssistant {
    private final AiProperties properties;

    public DisabledAiAssistant(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String provider() {
        return properties.resolvedProvider().id();
    }

    @Override
    public String model() {
        return properties.resolvedModel();
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED,
                "El análisis con IA no está configurado: defina AI_API_KEY");
    }
}
