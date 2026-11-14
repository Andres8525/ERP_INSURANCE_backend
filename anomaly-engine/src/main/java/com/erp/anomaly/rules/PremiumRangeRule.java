package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PolicyRecord;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

public final class PremiumRangeRule implements DetectionRule {
    static final BigDecimal MAX_MONTHLY_PREMIUM = new BigDecimal("2500.00");

    @Override
    public String id() {
        return "premium-range";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        return dataset.policies().stream()
                .filter(PolicyRecord::inForce)
                .flatMap(policy -> inspect(policy).stream());
    }

    private Optional<Finding> inspect(PolicyRecord policy) {
        BigDecimal premium = policy.premiumAmount();
        boolean missing = premium == null || premium.signum() <= 0;
        boolean tooHigh = !missing && premium.compareTo(MAX_MONTHLY_PREMIUM) > 0;
        if (!missing && !tooHigh) return Optional.empty();
        return Optional.of(Finding.builder(id())
                .type(FindingType.OTHER)
                .severity(missing ? Severity.RED : Severity.YELLOW)
                .source("Facturación")
                .confidence(missing ? 97 : 80)
                .policy(policy)
                .title(missing ? "Prima mensual sin valor" : "Prima mensual fuera de rango")
                .description("La prima de " + policy.planName() + " (" + policy.insurer() + ") requiere revisión.")
                .comparison("Prima mensual", premium == null ? "-" : premium.toPlainString(), "0.01 - " + MAX_MONTHLY_PREMIUM)
                .evidence("Rango esperado para planes ACA individuales")
                .build());
    }
}
