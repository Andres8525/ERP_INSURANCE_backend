package com.erp.anomaly.domain;

import com.erp.anomaly.domain.DomainTypes.PolicyState;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyRecord(String id, String patientId, String insurer, String planName, LocalDate coverageStart,
                           LocalDate coverageEnd, BigDecimal premiumAmount, PolicyState state) {
    public boolean inForce() {
        return state != PolicyState.TERMINATED;
    }

    public boolean overlaps(PolicyRecord other) {
        if (coverageStart == null || coverageEnd == null || other.coverageStart == null || other.coverageEnd == null) {
            return false;
        }
        return !coverageStart.isAfter(other.coverageEnd) && !other.coverageStart.isAfter(coverageEnd);
    }

    public String period() {
        return coverageStart + " a " + coverageEnd;
    }
}
