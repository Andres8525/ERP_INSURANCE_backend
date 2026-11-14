package com.erp.insurance.audit;

import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class AiPromptFactory {
    static final String SYSTEM = """
            Eres un auditor de calidad de datos para una agencia de seguros de salud ACA (Obamacare) en Estados Unidos.
            Recibes únicamente metadatos anonimizados de anomalías detectadas por un motor de reglas: nunca datos personales.
            Responde siempre en español, de forma concisa y accionable, en Markdown con estas secciones:
            ## Diagnóstico
            ## Prioridades
            ## Acciones recomendadas
            ## Prevención
            No inventes cifras que no estén en los datos recibidos.""";

    private AiPromptFactory() {}

    static AiPrompt insights(Collection<DataAnomalyEntity> anomalies, long totalOpen) {
        StringBuilder user = new StringBuilder()
                .append("Bandeja de auditoría: ").append(totalOpen).append(" anomalías abiertas; se analizan ")
                .append(anomalies.size()).append(".\n\n")
                .append("Por severidad (RED = crítica, YELLOW = revisión):\n")
                .append(bullets(count(anomalies, anomaly -> anomaly.getSeverity().name())))
                .append("\nPor tipo de registro afectado:\n")
                .append(bullets(count(anomalies, anomaly -> anomaly.getAffectedRecordType().name())))
                .append("\nPor hallazgo:\n")
                .append(bullets(count(anomalies, AiPromptFactory::describe)))
                .append("\nAutocorregibles sin intervención humana: ")
                .append(anomalies.stream().filter(AuditService::isAutoCorrectable).count())
                .append("\n\nPrioriza qué resolver primero, explica el riesgo operativo y regulatorio de cada grupo ")
                .append("y propone acciones concretas para el equipo de afiliación.");
        return new AiPrompt(SYSTEM, user.toString());
    }

    static AiPrompt explain(DataAnomalyEntity anomaly) {
        String user = """
                Explica esta anomalía a un agente de afiliación y dile cómo corregirla.
                - Hallazgo: %s
                - Tipo: %s
                - Severidad: %s
                - Registro afectado: %s
                - Campo: %s
                - Origen y confianza: %s, %s%%
                - Corrección sugerida disponible: %s
                Usa como máximo 150 palabras.""".formatted(
                anomaly.getTitle(), anomaly.getType(), anomaly.getSeverity(), anomaly.getAffectedRecordType(),
                orDash(anomaly.getFieldLabel()), orDash(anomaly.getSource()),
                anomaly.getConfidence() == null ? "-" : anomaly.getConfidence(),
                anomaly.getSuggestedCorrectionField() == null ? "no" : "sí, en el campo " + anomaly.getSuggestedCorrectionField());
        return new AiPrompt(SYSTEM, user);
    }

    private static String describe(DataAnomalyEntity anomaly) {
        String field = anomaly.getFieldLabel() == null ? "" : " [" + anomaly.getFieldLabel() + "]";
        return anomaly.getType() + " · " + anomaly.getTitle() + field;
    }

    private static Map<String, Long> count(Collection<DataAnomalyEntity> anomalies,
                                           java.util.function.Function<DataAnomalyEntity, String> key) {
        return anomalies.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    private static String bullets(Map<String, Long> counts) {
        if (counts.isEmpty()) return "- (ninguna)\n";
        return counts.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
