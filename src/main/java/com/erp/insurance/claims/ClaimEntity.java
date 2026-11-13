package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimPriority;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.security.SensitiveDataConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class ClaimEntity {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "claim_number", nullable = false, unique = true, length = 80)
    private String claimNumber;
    @Convert(converter = SensitiveDataConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClaimStatus status = ClaimStatus.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClaimPriority priority = ClaimPriority.NORMAL;
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(length = 120)
    private String location;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scheduledAt ASC")
    private List<ClaimServiceEntity> services = new ArrayList<>();
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<ClaimStatusHistoryEntity> history = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ClaimEntity() {}

    public ClaimEntity(String claimNumber, PatientEntity patient, String description) {
        this.claimNumber = claimNumber;
        this.patient = patient;
        this.description = description;
    }

    /** Cambia el estado y deja constancia en el historial. */
    public void changeStatus(ClaimStatus next, String note) {
        ClaimStatus previous = history.isEmpty() ? null : status;
        if (previous == next) return;
        status = next;
        updatedAt = Instant.now();
        history.add(new ClaimStatusHistoryEntity(this, previous, next, note, updatedAt));
    }

    public void addService(String name, String details, Instant scheduledAt) {
        services.add(new ClaimServiceEntity(this, name, details, scheduledAt));
    }

    public void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public ClaimStatus getStatus() { return status; }
    public ClaimPriority getPriority() { return priority; }
    public void setPriority(ClaimPriority value) { priority = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity value) { patient = value; }
    public List<ClaimServiceEntity> getServices() { return services; }
    public List<ClaimStatusHistoryEntity> getHistory() { return history; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Permite fechar datos semilla en el pasado. */
    public void backdate(Instant created, Instant updated) {
        createdAt = created;
        updatedAt = updated;
        history.forEach(entry -> entry.backdate(updated));
    }
}
