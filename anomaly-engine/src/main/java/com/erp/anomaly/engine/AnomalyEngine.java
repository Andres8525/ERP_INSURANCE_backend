package com.erp.anomaly.engine;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.rules.BirthDateRule;
import com.erp.anomaly.rules.DetectionRule;
import com.erp.anomaly.rules.DuplicatePatientRule;
import com.erp.anomaly.rules.EmailFormatRule;
import com.erp.anomaly.rules.ExpiredActivePolicyRule;
import com.erp.anomaly.rules.PhoneFormatRule;
import com.erp.anomaly.rules.PolicyOverlapRule;
import com.erp.anomaly.rules.PremiumRangeRule;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class AnomalyEngine {
    public static final String VERSION = "anomaly-engine/1.0.0";

    private static final Comparator<Finding> PRIORITY = Comparator
            .comparing(Finding::severity)
            .thenComparing(Comparator.comparingInt(Finding::confidence).reversed())
            .thenComparing(Finding::recordId);

    private final List<DetectionRule> rules;

    public AnomalyEngine(List<DetectionRule> rules) {
        if (rules.isEmpty()) throw new IllegalArgumentException("At least one detection rule is required");
        this.rules = List.copyOf(rules);
    }

    public static AnomalyEngine withDefaultRules() {
        return new AnomalyEngine(List.of(
                new EmailFormatRule(),
                new PhoneFormatRule(),
                new BirthDateRule(),
                new DuplicatePatientRule(),
                new PolicyOverlapRule(),
                new ExpiredActivePolicyRule(),
                new PremiumRangeRule()));
    }

    public EngineReport analyze(Dataset dataset) {
        return new EngineReport(stream(dataset).sorted(PRIORITY).toList(), dataset.size(), VERSION, ruleIds());
    }

    public Stream<Finding> stream(Dataset dataset) {
        return rules.stream().flatMap(rule -> rule.evaluate(dataset));
    }

    public List<String> ruleIds() {
        return rules.stream().map(DetectionRule::id).toList();
    }
}
