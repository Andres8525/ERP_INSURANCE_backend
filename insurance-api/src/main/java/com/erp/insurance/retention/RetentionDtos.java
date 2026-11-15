package com.erp.insurance.retention;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RetentionDtos {
    private RetentionDtos() {}

    public enum RetentionDecision { KEEP, ARCHIVE, DELETE, LEGAL_HOLD }

    public enum RecordCategory {
        POLICY("Pólizas"), CLAIM("Siniestros"), PATIENT_FILE("Expedientes de paciente");

        private final String label;

        RecordCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record RetentionCandidate(UUID recordId, RecordCategory category, String reference, String crmId,
                                     LocalDate lastActivity, int inactiveMonths, double sizeMb,
                                     RetentionDecision decision, String reason) {}

    public record CategoryBreakdown(RecordCategory category, String label, int records, double sizeMb, int keep,
                                    int archive, int delete, int legalHold) {}

    public record RetentionSummary(int profiled, int keep, int archive, int delete, int legalHold,
                                   double storageGb, double archiveGb, double deleteGb,
                                   double monthlyCostUsd, double optimizedMonthlyCostUsd, double monthlySavingsUsd,
                                   double savingsPercent, double annualSavingsPer10kRecordsUsd) {}

    public record RetentionRules(int archiveAfterMonths, int retentionYears, double hotUsdPerGbMonth,
                                 double archiveUsdPerGbMonth) {}

    public record RetentionProfile(RetentionSummary summary, RetentionRules rules, List<CategoryBreakdown> categories,
                                   List<RetentionCandidate> candidates, Instant generatedAt) {}
}
