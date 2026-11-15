package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptFactoryTest {
    private static DataAnomalyEntity anomaly(AnomalyType type, AnomalySeverity severity, String title,
                                             AffectedRecordType record, String field, String value) {
        return new DataAnomalyEntity(type, severity, title, "descripción con Ana Pérez", record, UUID.randomUUID(), null)
                .withAnalysis("CRM", 90, field, value, "otro-" + value, "evidencia");
    }

    private final List<DataAnomalyEntity> sample = List.of(
            anomaly(AnomalyType.OTHER, AnomalySeverity.YELLOW, "Teléfono con formato inválido", AffectedRecordType.PATIENT, "Teléfono", "305-555-01"),
            anomaly(AnomalyType.POLICY_OVERLAP, AnomalySeverity.RED, "Pólizas solapadas", AffectedRecordType.POLICY, "Inicio de cobertura", "2026-01-01"),
            anomaly(AnomalyType.POLICY_OVERLAP, AnomalySeverity.RED, "Pólizas solapadas", AffectedRecordType.POLICY, "Vigencia", "2026-02-01"),
            anomaly(AnomalyType.DUPLICATE, AnomalySeverity.YELLOW, "Posible cliente duplicado", AffectedRecordType.PATIENT, "Identidad del miembro", "Lucía Martínez"));

    @Test
    void groupsFindingsByUrgencyWithSpanishLabels() {
        List<String> groups = AiPromptFactory.groups(sample);

        assertThat(groups).hasSize(3);
        assertThat(groups.getFirst()).isEqualTo(
                "Pólizas solapadas: 2 caso(s), severidad crítica, categoría pólizas solapadas, registro póliza, campo Inicio de cobertura, Vigencia");
        assertThat(groups.get(1)).startsWith("Posible cliente duplicado: 1 caso(s), severidad revisión");
    }

    @Test
    void promptUsesReadableLabelsAndNeverPersonalData() {
        AiPrompt prompt = AiPromptFactory.insights(sample, 9);

        assertThat(prompt.user())
                .contains("9 anomalías abiertas", "2 críticas y 2 en revisión", "## Prioridades")
                .doesNotContain("RED", "YELLOW", "PATIENT", "POLICY_OVERLAP", "Ana Pérez", "Lucía", "305-555");
        assertThat(prompt.system()).contains("Nunca uses tablas, HTML");
    }

    @Test
    void explanationDescribesTheAnomalyInSpanish() {
        AiPrompt prompt = AiPromptFactory.explain(sample.get(1));
        assertThat(prompt.user())
                .contains("Severidad: Crítica", "Registro afectado: Póliza", "Categoría: Pólizas solapadas", "## Cómo corregirlo")
                .doesNotContain("2026-01-01", "POLICY");
    }
}
