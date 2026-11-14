package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.domain.DomainEnums.BatchResolutionAction;
import com.erp.insurance.patients.PatientEntity;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contratos JSON del módulo de auditoría. Se reflejan 1:1 en
 * fronted/Erp_Insurance/src/app/core/api/api.models.ts.
 */
public final class AuditDtos {
    private AuditDtos() {}

    public record ResolveBatchRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> ids,
                                      @NotNull BatchResolutionAction action) {}

    public record AnomalyPatient(UUID id, String fullName) {
        static AnomalyPatient from(PatientEntity patient) {
            return patient == null ? null
                    : new AnomalyPatient(patient.getId(), patient.getFirstName() + " " + patient.getLastName());
        }
    }

    public record AnomalyResponse(UUID id, AnomalyType type, AnomalySeverity severity, String title,
                                  String description, AnomalyResolutionStatus resolutionStatus,
                                  AffectedRecordType affectedRecordType, UUID affectedRecordId,
                                  AnomalyPatient patient, String source, Integer confidence, String fieldLabel,
                                  String currentValue, String conflictingValue, String evidence,
                                  String suggestedCorrectionField, String suggestedCorrectionValue,
                                  boolean autoCorrectable, Instant createdAt) {
        static AnomalyResponse from(DataAnomalyEntity entity) {
            return new AnomalyResponse(entity.getId(), entity.getType(), entity.getSeverity(), entity.getTitle(),
                    entity.getDescription(), entity.getResolutionStatus(), entity.getAffectedRecordType(),
                    entity.getAffectedRecordId(), AnomalyPatient.from(entity.getPatient()), entity.getSource(),
                    entity.getConfidence(), entity.getFieldLabel(), entity.getCurrentValue(),
                    entity.getConflictingValue(), entity.getEvidence(), entity.getSuggestedCorrectionField(),
                    entity.getSuggestedCorrectionValue(), AuditService.isAutoCorrectable(entity),
                    entity.getCreatedAt());
        }
    }

    public record ResolveBatchResponse(int processed, BatchResolutionAction action, List<UUID> ids) {}

    public record AuditSummaryResponse(long open, long critical, long review, long resolvedThisMonth) {}
}
