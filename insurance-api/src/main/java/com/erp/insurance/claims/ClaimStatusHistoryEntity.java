package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Historial de cambios de estado de un siniestro. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistoryEntity {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private ClaimEntity claim;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 24)
    private ClaimStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 24)
    private ClaimStatus toStatus;
    @Column(length = 255)
    private String note;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ClaimStatusHistoryEntity() {}

    ClaimStatusHistoryEntity(ClaimEntity claim, ClaimStatus fromStatus, ClaimStatus toStatus, String note, Instant changedAt) {
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedAt = changedAt;
    }

    void backdate(Instant value) {
        if (changedAt.isAfter(value)) changedAt = value;
    }

    public UUID getId() { return id; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
