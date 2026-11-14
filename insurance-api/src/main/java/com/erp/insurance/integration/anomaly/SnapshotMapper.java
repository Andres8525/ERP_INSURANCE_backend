package com.erp.insurance.integration.anomaly;

import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.Finding;
import com.erp.grpc.anomaly.v1.PatientSnapshot;
import com.erp.grpc.anomaly.v1.PolicySnapshot;
import com.erp.grpc.anomaly.v1.PolicyStatus;
import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.policies.PolicyEntity;

import java.time.LocalDate;
import java.util.Collection;

public final class SnapshotMapper {
    private SnapshotMapper() {}

    public static AnalyzeRequest request(Collection<PatientEntity> patients, Collection<PolicyEntity> policies,
                                         LocalDate referenceDate) {
        return AnalyzeRequest.newBuilder()
                .addAllPatients(patients.stream().map(SnapshotMapper::patient).toList())
                .addAllPolicies(policies.stream().map(SnapshotMapper::policy).toList())
                .setReferenceDate(referenceDate.toString())
                .build();
    }

    static PatientSnapshot patient(PatientEntity entity) {
        return PatientSnapshot.newBuilder()
                .setId(entity.getId().toString())
                .setFirstName(text(entity.getFirstName()))
                .setLastName(text(entity.getLastName()))
                .setEmail(text(entity.getEmail()))
                .setPhone(text(entity.getPhone()))
                .setDateOfBirth(text(entity.getDateOfBirth()))
                .build();
    }

    static PolicySnapshot policy(PolicyEntity entity) {
        return PolicySnapshot.newBuilder()
                .setId(entity.getId().toString())
                .setPatientId(entity.getPatient().getId().toString())
                .setInsurer(text(entity.getInsurer()))
                .setPlanName(text(entity.getPlanName()))
                .setCoverageStart(entity.getCoverageStart() == null ? "" : entity.getCoverageStart().toString())
                .setCoverageEnd(entity.getCoverageEnd() == null ? "" : entity.getCoverageEnd().toString())
                .setPremiumAmount(entity.getPremiumAmount() == null ? "" : entity.getPremiumAmount().toPlainString())
                .setStatus(switch (entity.getStatus()) {
                    case PENDING -> PolicyStatus.POLICY_STATUS_PENDING;
                    case ACTIVE -> PolicyStatus.POLICY_STATUS_ACTIVE;
                    case TERMINATED -> PolicyStatus.POLICY_STATUS_TERMINATED;
                })
                .build();
    }

    public static AnomalyType type(Finding finding) {
        return switch (finding.getType()) {
            case FINDING_TYPE_POLICY_OVERLAP -> AnomalyType.POLICY_OVERLAP;
            case FINDING_TYPE_DUPLICATE -> AnomalyType.DUPLICATE;
            case FINDING_TYPE_OBSOLETE_FILE -> AnomalyType.OBSOLETE_FILE;
            case FINDING_TYPE_OTHER, FINDING_TYPE_UNSPECIFIED, UNRECOGNIZED -> AnomalyType.OTHER;
        };
    }

    public static AnomalySeverity severity(Finding finding) {
        return finding.getSeverity() == com.erp.grpc.anomaly.v1.Severity.SEVERITY_RED
                ? AnomalySeverity.RED : AnomalySeverity.YELLOW;
    }

    public static AffectedRecordType recordType(Finding finding) {
        return finding.getRecordType() == com.erp.grpc.anomaly.v1.RecordType.RECORD_TYPE_POLICY
                ? AffectedRecordType.POLICY : AffectedRecordType.PATIENT;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
