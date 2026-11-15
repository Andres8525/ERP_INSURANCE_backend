package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import com.erp.insurance.retention.RetentionDtos.CategoryBreakdown;
import com.erp.insurance.retention.RetentionDtos.RecordCategory;
import com.erp.insurance.retention.RetentionDtos.RetentionCandidate;
import com.erp.insurance.retention.RetentionDtos.RetentionDecision;
import com.erp.insurance.retention.RetentionDtos.RetentionProfile;
import com.erp.insurance.retention.RetentionDtos.RetentionRules;
import com.erp.insurance.retention.RetentionDtos.RetentionSummary;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class AiPromptFactory {
    static final String SYSTEM = """
            Eres auditor de calidad de datos de una agencia de seguros de salud ACA (Obamacare) en Estados Unidos.
            Recibes únicamente metadatos anonimizados de anomalías detectadas por un motor de reglas, nunca datos personales.
            Reglas de estilo obligatorias:
            - Español neutro y profesional, frases cortas y concretas.
            - Usa solo las cifras recibidas; no inventes datos, porcentajes ni normas.
            - Responde siempre en español, aunque los datos contengan términos en inglés.
            - Nunca uses tablas, HTML, etiquetas <br>, Markdown, separadores --- ni emojis.
            - Nunca muestres códigos internos en mayúsculas (RED, YELLOW, PATIENT, POLICY, POLICY_OVERLAP...): usa las etiquetas en español que recibes.""";

    static final String INSIGHTS_FORMAT = """
            Responde solo con un objeto JSON válido, sin texto antes ni después, con esta forma:
            {
              "summary": "2 o 3 frases sobre el estado de la bandeja",
              "impact": "1 frase sobre el riesgo operativo y regulatorio global",
              "riskLevel": "ALTO | MEDIO | BAJO",
              "priorities": [
                {"title": "nombre del grupo tal como se recibió", "severity": "Crítica | Revisión | Mixta",
                 "recordType": "Paciente | Póliza | Siniestro", "count": 2,
                 "risk": "riesgo en máximo 20 palabras", "action": "acción inmediata en máximo 20 palabras"}
              ],
              "actions": [{"title": "verbo + acción concreta", "owner": "Afiliación | Facturación | Auditoría | Atención al cliente | TI", "detail": "máximo 25 palabras"}],
              "prevention": ["control preventivo en máximo 20 palabras"]
            }
            priorities: un elemento por grupo recibido, en el mismo orden de urgencia (máximo 6).
            actions: entre 3 y 5. prevention: entre 2 y 4.""";

    static final String EXPLAIN_FORMAT = """
            Responde solo con un objeto JSON válido, sin texto antes ni después, con esta forma:
            {
              "summary": "qué ocurre, en 1 o 2 frases",
              "impact": "por qué importa, en 1 frase",
              "riskLevel": "ALTO | MEDIO | BAJO",
              "actions": [{"title": "paso concreto que empieza con un verbo", "owner": "equipo responsable", "detail": "máximo 25 palabras"}],
              "prevention": ["control para que no se repita"]
            }
            actions: entre 2 y 4 pasos en orden. prevention: 1 o 2 elementos.""";

    static final String RETENTION_FORMAT = """
            Responde solo con un objeto JSON válido, sin texto antes ni después, con esta forma:
            {
              "summary": "2 o 3 frases sobre el estado del archivo y el ahorro posible",
              "impact": "1 frase sobre el riesgo de cumplimiento (retención de datos) y de costos",
              "riskLevel": "ALTO | MEDIO | BAJO",
              "priorities": [
                {"title": "lote concreto, por ejemplo Pólizas con retención cumplida", "severity": "Crítica | Revisión",
                 "recordType": "Pólizas | Siniestros | Expedientes de paciente", "count": 3,
                 "risk": "riesgo de no actuar en máximo 20 palabras", "action": "eliminar, archivar o conservar, en máximo 20 palabras"}
              ],
              "actions": [{"title": "verbo + acción concreta", "owner": "Cumplimiento | Auditoría | TI | Legal", "detail": "máximo 25 palabras"}],
              "prevention": ["control para que el archivo no vuelva a crecer sin gobierno, máximo 20 palabras"]
            }
            priorities: un lote por combinación de categoría y decisión con registros (máximo 6); severidad Crítica para eliminaciones y retenciones legales, Revisión para archivos.
            actions: entre 3 y 5, incluye respaldo previo, aprobación y registro de auditoría de la eliminación. prevention: entre 2 y 4.""";

    private AiPromptFactory() {}

    static AiPrompt insights(Collection<DataAnomalyEntity> anomalies, long totalOpen) {
        long critical = anomalies.stream().filter(anomaly -> anomaly.getSeverity() == AnomalySeverity.RED).count();
        StringBuilder user = new StringBuilder()
                .append("Bandeja de auditoría: ").append(totalOpen).append(" anomalías abiertas, se analizan ")
                .append(anomalies.size()).append(" (").append(critical).append(" críticas y ")
                .append(anomalies.size() - critical).append(" en revisión).\n")
                .append("Autocorregibles sin intervención humana: ")
                .append(anomalies.stream().filter(AuditService::isAutoCorrectable).count()).append(".\n\n")
                .append("Grupos de hallazgos (de mayor a menor urgencia):\n");
        groups(anomalies).forEach(group -> user.append("- ").append(group).append('\n'));
        user.append('\n').append(INSIGHTS_FORMAT);
        return new AiPrompt(SYSTEM, user.toString(), true);
    }

    static AiPrompt explain(DataAnomalyEntity anomaly) {
        String user = """
                Explica esta anomalía a un agente de afiliación y dile cómo corregirla.
                - Hallazgo: %s
                - Categoría: %s
                - Severidad: %s
                - Registro afectado: %s
                - Campo: %s
                - Origen y confianza: %s, %s%%
                - Corrección automática disponible: %s

                %s""".formatted(
                anomaly.getTitle(), typeLabel(anomaly.getType()), severityLabel(anomaly.getSeverity()),
                recordLabel(anomaly.getAffectedRecordType()), orDash(anomaly.getFieldLabel()), orDash(anomaly.getSource()),
                anomaly.getConfidence() == null ? "-" : anomaly.getConfidence(),
                AuditService.isAutoCorrectable(anomaly) ? "sí" : "no", EXPLAIN_FORMAT);
        return new AiPrompt(SYSTEM, user, true);
    }

    static AiPrompt retention(RetentionProfile profile) {
        RetentionSummary summary = profile.summary();
        RetentionRules rules = profile.rules();
        StringBuilder user = new StringBuilder()
                .append("Perfil de retención de una corredora de seguros médicos: ").append(summary.profiled())
                .append(" registros analizados (").append(String.format(Locale.ROOT, "%.2f", summary.storageGb())).append(" GB).\n")
                .append("Política: archivar tras ").append(rules.archiveAfterMonths()).append(" meses sin actividad y eliminar tras ")
                .append(rules.retentionYears()).append(" años; un siniestro en trámite o una anomalía crítica abierta impone retención legal.\n")
                .append("Decisiones: mantener ").append(summary.keep()).append(", archivar ").append(summary.archive())
                .append(" (").append(String.format(Locale.ROOT, "%.2f", summary.archiveGb())).append(" GB), eliminar ")
                .append(summary.delete()).append(" (").append(String.format(Locale.ROOT, "%.2f", summary.deleteGb()))
                .append(" GB), retención legal ").append(summary.legalHold()).append(".\n")
                .append("Costo de almacenamiento estimado: ").append(usd(summary.monthlyCostUsd())).append(" al mes hoy, ")
                .append(usd(summary.optimizedMonthlyCostUsd())).append(" tras optimizar (ahorro ")
                .append(String.format(Locale.ROOT, "%.1f", summary.savingsPercent())).append(" %); proyección anual por cada 10.000 registros: ")
                .append(usd(summary.annualSavingsPer10kRecordsUsd())).append(".\n\nLotes por categoría:\n");
        for (CategoryBreakdown category : profile.categories()) {
            user.append("- ").append(category.label()).append(": ").append(category.records()).append(" registros; archivar ")
                    .append(category.archive()).append(", eliminar ").append(category.delete()).append(", retención legal ")
                    .append(category.legalHold()).append(inactivity(profile, category.category())).append('\n');
        }
        long claimHolds = profile.candidates().stream().filter(candidate -> candidate.decision() == RetentionDecision.LEGAL_HOLD
                && candidate.reason().startsWith("Siniestro")).count();
        user.append("Motivos de retención legal: ").append(claimHolds).append(" por siniestros en trámite, ")
                .append(summary.legalHold() - claimHolds).append(" por anomalías críticas abiertas.\n\n")
                .append(RETENTION_FORMAT);
        return new AiPrompt(SYSTEM, user.toString(), true);
    }

    private static String inactivity(RetentionProfile profile, RecordCategory category) {
        var months = profile.candidates().stream().filter(candidate -> candidate.category() == category)
                .mapToInt(RetentionCandidate::inactiveMonths).summaryStatistics();
        if (months.getCount() == 0) return "";
        return "; inactividad entre " + months.getMin() / 12 + " y " + months.getMax() / 12 + " años";
    }

    private static String usd(double value) {
        return String.format(Locale.ROOT, "US$%.2f", value);
    }

    static List<String> groups(Collection<DataAnomalyEntity> anomalies) {
        Map<String, List<DataAnomalyEntity>> byTitle = anomalies.stream().collect(Collectors.groupingBy(
                anomaly -> Objects.requireNonNullElse(anomaly.getTitle(), typeLabel(anomaly.getType())),
                LinkedHashMap::new, Collectors.toList()));
        return byTitle.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<DataAnomalyEntity>>>comparingLong(entry -> entry.getValue().stream()
                                .filter(anomaly -> anomaly.getSeverity() == AnomalySeverity.RED).count()).reversed()
                        .thenComparing(entry -> -entry.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> describe(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String describe(String title, List<DataAnomalyEntity> group) {
        long critical = group.stream().filter(anomaly -> anomaly.getSeverity() == AnomalySeverity.RED).count();
        String severity = critical == group.size() ? "crítica" : critical == 0 ? "revisión" : "mixta (" + critical + " críticas)";
        String records = group.stream().map(anomaly -> recordLabel(anomaly.getAffectedRecordType()).toLowerCase())
                .collect(Collectors.toCollection(TreeSet::new)).stream().collect(Collectors.joining(", "));
        String fields = group.stream().map(DataAnomalyEntity::getFieldLabel).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new)).stream().collect(Collectors.joining(", "));
        long autoCorrectable = group.stream().filter(AuditService::isAutoCorrectable).count();
        return title + ": " + group.size() + " caso(s), severidad " + severity + ", categoría "
                + typeLabel(group.getFirst().getType()).toLowerCase() + ", registro " + records
                + (fields.isEmpty() ? "" : ", campo " + fields)
                + (autoCorrectable > 0 ? ", " + autoCorrectable + " autocorregible(s)" : "");
    }

    static String severityLabel(AnomalySeverity severity) {
        return severity == AnomalySeverity.RED ? "Crítica" : "Revisión";
    }

    static String recordLabel(AffectedRecordType type) {
        return switch (type) {
            case PATIENT -> "Paciente";
            case POLICY -> "Póliza";
            case CLAIM -> "Siniestro";
        };
    }

    static String typeLabel(AnomalyType type) {
        return switch (type) {
            case POLICY_OVERLAP -> "Pólizas solapadas";
            case DUPLICATE -> "Registro duplicado";
            case OBSOLETE_FILE -> "Documento obsoleto";
            case OTHER -> "Calidad de datos";
        };
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
