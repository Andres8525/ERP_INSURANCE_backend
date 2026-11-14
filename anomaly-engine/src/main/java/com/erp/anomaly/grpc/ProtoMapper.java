package com.erp.anomaly.grpc;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.PolicyState;
import com.erp.anomaly.domain.DomainTypes.RecordType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PatientRecord;
import com.erp.anomaly.domain.PolicyRecord;
import com.erp.anomaly.engine.EngineReport;
import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.AnalyzeResponse;
import com.erp.grpc.anomaly.v1.PatientSnapshot;
import com.erp.grpc.anomaly.v1.PolicySnapshot;
import com.erp.grpc.anomaly.v1.PolicyStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class ProtoMapper {
    private ProtoMapper() {}

    public static Dataset toDataset(AnalyzeRequest request, Clock clock) {
        LocalDate reference = request.getReferenceDate().isBlank()
                ? LocalDate.now(clock)
                : date("reference_date", request.getReferenceDate());
        return new Dataset(
                request.getPatientsList().stream().map(ProtoMapper::toPatient).toList(),
                request.getPoliciesList().stream().map(ProtoMapper::toPolicy).toList(),
                reference);
    }

    static PatientRecord toPatient(PatientSnapshot snapshot) {
        return new PatientRecord(required("patient.id", snapshot.getId()), snapshot.getFirstName(),
                snapshot.getLastName(), snapshot.getEmail(), snapshot.getPhone(),
                optionalDate("patient.date_of_birth", snapshot.getDateOfBirth()));
    }

    static PolicyRecord toPolicy(PolicySnapshot snapshot) {
        return new PolicyRecord(required("policy.id", snapshot.getId()),
                required("policy.patient_id", snapshot.getPatientId()), snapshot.getInsurer(), snapshot.getPlanName(),
                optionalDate("policy.coverage_start", snapshot.getCoverageStart()),
                optionalDate("policy.coverage_end", snapshot.getCoverageEnd()),
                amount(snapshot.getPremiumAmount()), state(snapshot.getStatus()));
    }

    public static AnalyzeResponse toResponse(EngineReport report) {
        return AnalyzeResponse.newBuilder()
                .addAllFindings(report.findings().stream().map(ProtoMapper::toProto).toList())
                .setAnalyzedRecords(report.analyzedRecords())
                .setEngineVersion(report.engineVersion())
                .addAllAppliedRules(report.appliedRules())
                .build();
    }

    public static com.erp.grpc.anomaly.v1.Finding toProto(Finding finding) {
        return com.erp.grpc.anomaly.v1.Finding.newBuilder()
                .setRule(finding.rule())
                .setType(type(finding.type()))
                .setSeverity(severity(finding.severity()))
                .setRecordType(recordType(finding.recordType()))
                .setRecordId(finding.recordId())
                .setPatientId(orEmpty(finding.patientId()))
                .setTitle(orEmpty(finding.title()))
                .setDescription(orEmpty(finding.description()))
                .setSource(orEmpty(finding.source()))
                .setConfidence(finding.confidence())
                .setFieldLabel(orEmpty(finding.fieldLabel()))
                .setCurrentValue(orEmpty(finding.currentValue()))
                .setConflictingValue(orEmpty(finding.conflictingValue()))
                .setEvidence(orEmpty(finding.evidence()))
                .setSuggestedField(orEmpty(finding.suggestedField()))
                .setSuggestedValue(orEmpty(finding.suggestedValue()))
                .build();
    }

    private static com.erp.grpc.anomaly.v1.FindingType type(FindingType type) {
        return switch (type) {
            case POLICY_OVERLAP -> com.erp.grpc.anomaly.v1.FindingType.FINDING_TYPE_POLICY_OVERLAP;
            case DUPLICATE -> com.erp.grpc.anomaly.v1.FindingType.FINDING_TYPE_DUPLICATE;
            case OBSOLETE_FILE -> com.erp.grpc.anomaly.v1.FindingType.FINDING_TYPE_OBSOLETE_FILE;
            case OTHER -> com.erp.grpc.anomaly.v1.FindingType.FINDING_TYPE_OTHER;
        };
    }

    private static com.erp.grpc.anomaly.v1.Severity severity(Severity severity) {
        return switch (severity) {
            case RED -> com.erp.grpc.anomaly.v1.Severity.SEVERITY_RED;
            case YELLOW -> com.erp.grpc.anomaly.v1.Severity.SEVERITY_YELLOW;
        };
    }

    private static com.erp.grpc.anomaly.v1.RecordType recordType(RecordType type) {
        return switch (type) {
            case PATIENT -> com.erp.grpc.anomaly.v1.RecordType.RECORD_TYPE_PATIENT;
            case POLICY -> com.erp.grpc.anomaly.v1.RecordType.RECORD_TYPE_POLICY;
        };
    }

    private static PolicyState state(PolicyStatus status) {
        return switch (status) {
            case POLICY_STATUS_ACTIVE -> PolicyState.ACTIVE;
            case POLICY_STATUS_TERMINATED -> PolicyState.TERMINATED;
            case POLICY_STATUS_PENDING, POLICY_STATUS_UNSPECIFIED, UNRECOGNIZED -> PolicyState.PENDING;
        };
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static LocalDate optionalDate(String field, String value) {
        return value == null || value.isBlank() ? null : date(field, value);
    }

    private static LocalDate date(String field, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    private static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("policy.premium_amount must be a decimal number");
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
