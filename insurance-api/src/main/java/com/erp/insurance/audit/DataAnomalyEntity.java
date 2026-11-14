package com.erp.insurance.audit;

import com.erp.insurance.auth.UserEntity;
import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.domain.DomainEnums.BatchResolutionAction;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.security.SensitiveDataConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_anomalies")
public class DataAnomalyEntity {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private AnomalyType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private AnomalySeverity severity;
    @Column(length = 160)
    private String title;
    @Convert(converter = SensitiveDataConverter.class) @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING) @Column(name = "resolution_status", nullable = false, length = 20)
    private AnomalyResolutionStatus resolutionStatus = AnomalyResolutionStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(name = "affected_record_type", nullable = false, length = 20)
    private AffectedRecordType affectedRecordType;
    @Column(name = "affected_record_id", nullable = false)
    private UUID affectedRecordId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "patient_id")
    private PatientEntity patient;

    @Column(length = 40)
    private String source;

    @Column
    private Integer confidence;

    @Column(name = "field_label", length = 120)
    private String fieldLabel;
    @Convert(converter = SensitiveDataConverter.class) @Column(name = "current_value", columnDefinition = "text")
    private String currentValue;
    @Convert(converter = SensitiveDataConverter.class) @Column(name = "conflicting_value", columnDefinition = "text")
    private String conflictingValue;
    @Column(length = 500)
    private String evidence;
    @Column(name = "suggested_correction_field", length = 80)
    private String suggestedCorrectionField;
    @Convert(converter = SensitiveDataConverter.class) @Column(name = "suggested_correction_value", columnDefinition = "text")
    private String suggestedCorrectionValue;
    @Enumerated(EnumType.STRING) @Column(name = "resolution_action", length = 32)
    private BatchResolutionAction resolutionAction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by_id")
    private UserEntity resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DataAnomalyEntity() {}

    public DataAnomalyEntity(AnomalyType type, AnomalySeverity severity, String title, String description,
                             AffectedRecordType affectedRecordType, UUID affectedRecordId, PatientEntity patient) {
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.affectedRecordType = affectedRecordType;
        this.affectedRecordId = affectedRecordId;
        this.patient = patient;
    }

    public DataAnomalyEntity withAnalysis(String source, int confidence, String fieldLabel, String currentValue,
                                          String conflictingValue, String evidence) {
        this.source = source;
        this.confidence = confidence;
        this.fieldLabel = fieldLabel;
        this.currentValue = currentValue;
        this.conflictingValue = conflictingValue;
        this.evidence = evidence;
        return this;
    }

    public DataAnomalyEntity withSuggestion(String field, String value) {
        this.suggestedCorrectionField = field;
        this.suggestedCorrectionValue = value;
        return this;
    }

    public DataAnomalyEntity detectedAt(Instant value) {
        this.createdAt = value;
        return this;
    }

    public UUID getId() { return id; }
    public AnomalyType getType() { return type; }
    public AnomalySeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AnomalyResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(AnomalyResolutionStatus value) { resolutionStatus = value; }
    public AffectedRecordType getAffectedRecordType() { return affectedRecordType; }
    public UUID getAffectedRecordId() { return affectedRecordId; }
    public PatientEntity getPatient() { return patient; }
    public String getSource() { return source; }
    public Integer getConfidence() { return confidence; }
    public String getFieldLabel() { return fieldLabel; }
    public String getCurrentValue() { return currentValue; }
    public String getConflictingValue() { return conflictingValue; }
    public String getEvidence() { return evidence; }
    public String getSuggestedCorrectionField() { return suggestedCorrectionField; }
    public String getSuggestedCorrectionValue() { return suggestedCorrectionValue; }
    public BatchResolutionAction getResolutionAction() { return resolutionAction; }
    public void setResolutionAction(BatchResolutionAction value) { resolutionAction = value; }
    public void setResolvedBy(UserEntity value) { resolvedBy = value; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant value) { resolvedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
}
