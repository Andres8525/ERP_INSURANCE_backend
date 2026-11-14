package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PolicyRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PolicyOverlapRule implements DetectionRule {
    @Override
    public String id() {
        return "policy-overlap";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        Map<String, List<PolicyRecord>> byPatient = dataset.policies().stream()
                .filter(PolicyRecord::inForce)
                .filter(policy -> policy.coverageStart() != null && policy.coverageEnd() != null)
                .collect(Collectors.groupingBy(PolicyRecord::patientId));
        List<Finding> findings = new ArrayList<>();
        for (List<PolicyRecord> policies : byPatient.values()) {
            List<PolicyRecord> sorted = policies.stream()
                    .sorted(Comparator.comparing(PolicyRecord::coverageStart).thenComparing(PolicyRecord::id))
                    .toList();
            Set<String> reported = new HashSet<>();
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    PolicyRecord earlier = sorted.get(i);
                    PolicyRecord later = sorted.get(j);
                    if (earlier.overlaps(later) && reported.add(later.id())) findings.add(overlap(later, earlier));
                }
            }
        }
        return findings.stream();
    }

    private Finding overlap(PolicyRecord policy, PolicyRecord other) {
        return Finding.builder(id())
                .type(FindingType.POLICY_OVERLAP)
                .severity(Severity.RED)
                .source("Marketplace")
                .confidence(94)
                .policy(policy)
                .title("Pólizas solapadas")
                .description("La póliza " + policy.planName() + " de " + policy.insurer()
                        + " se cruza con " + other.planName() + " de " + other.insurer() + ".")
                .comparison("Vigencia", policy.period(), other.period())
                .evidence("Dos pólizas vigentes para el mismo asegurado")
                .build();
    }
}
