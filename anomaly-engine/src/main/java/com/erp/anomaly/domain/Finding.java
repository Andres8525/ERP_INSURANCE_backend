package com.erp.anomaly.domain;

import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.RecordType;
import com.erp.anomaly.domain.DomainTypes.Severity;

import java.util.Objects;

public record Finding(String rule, FindingType type, Severity severity, RecordType recordType, String recordId,
                      String patientId, String title, String description, String source, int confidence,
                      String fieldLabel, String currentValue, String conflictingValue, String evidence,
                      String suggestedField, String suggestedValue) {

    public Finding {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(recordId, "recordId");
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("confidence must be 0-100");
    }

    public boolean hasSuggestion() {
        return suggestedField != null && suggestedValue != null;
    }

    public static Builder builder(String rule) {
        return new Builder(rule);
    }

    public static final class Builder {
        private final String rule;
        private FindingType type = FindingType.OTHER;
        private Severity severity = Severity.YELLOW;
        private RecordType recordType;
        private String recordId;
        private String patientId;
        private String title;
        private String description;
        private String source = "Motor IA";
        private int confidence = 80;
        private String fieldLabel;
        private String currentValue;
        private String conflictingValue;
        private String evidence;
        private String suggestedField;
        private String suggestedValue;

        private Builder(String rule) {
            this.rule = rule;
        }

        public Builder type(FindingType value) { type = value; return this; }
        public Builder severity(Severity value) { severity = value; return this; }
        public Builder source(String value) { source = value; return this; }
        public Builder confidence(int value) { confidence = value; return this; }
        public Builder title(String value) { title = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder evidence(String value) { evidence = value; return this; }

        public Builder patient(PatientRecord patient) {
            recordType = RecordType.PATIENT;
            recordId = patient.id();
            patientId = patient.id();
            return this;
        }

        public Builder policy(PolicyRecord policy) {
            recordType = RecordType.POLICY;
            recordId = policy.id();
            patientId = policy.patientId();
            return this;
        }

        public Builder comparison(String label, String current, String conflicting) {
            fieldLabel = label;
            currentValue = current;
            conflictingValue = conflicting;
            return this;
        }

        public Builder suggestion(String field, String value) {
            suggestedField = field;
            suggestedValue = value;
            return this;
        }

        public Finding build() {
            return new Finding(rule, type, severity, recordType, recordId, patientId, title, description, source,
                    confidence, fieldLabel, currentValue, conflictingValue, evidence, suggestedField, suggestedValue);
        }
    }
}
