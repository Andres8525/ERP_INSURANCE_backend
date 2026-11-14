package com.erp.anomaly.engine;

import com.erp.anomaly.domain.Finding;

import java.util.List;

public record EngineReport(List<Finding> findings, int analyzedRecords, String engineVersion,
                           List<String> appliedRules) {
    public EngineReport {
        findings = List.copyOf(findings);
        appliedRules = List.copyOf(appliedRules);
    }
}
