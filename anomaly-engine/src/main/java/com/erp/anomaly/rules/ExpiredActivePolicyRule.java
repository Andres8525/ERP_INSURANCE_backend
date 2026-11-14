package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.PolicyState;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;

import java.util.stream.Stream;

public final class ExpiredActivePolicyRule implements DetectionRule {
    @Override
    public String id() {
        return "expired-active-policy";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        return dataset.policies().stream()
                .filter(policy -> policy.state() == PolicyState.ACTIVE)
                .filter(policy -> policy.coverageEnd() != null && policy.coverageEnd().isBefore(dataset.referenceDate()))
                .map(policy -> Finding.builder(id())
                        .type(FindingType.OBSOLETE_FILE)
                        .severity(Severity.YELLOW)
                        .source("Archivo")
                        .confidence(88)
                        .policy(policy)
                        .title("Póliza vencida marcada como activa")
                        .description("La póliza " + policy.planName() + " terminó el " + policy.coverageEnd()
                                + " y sigue en estado activo.")
                        .comparison("Fin de cobertura", policy.coverageEnd().toString(), dataset.referenceDate().toString())
                        .evidence("Fecha de referencia del análisis: " + dataset.referenceDate())
                        .build());
    }
}
