package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimPriority;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos JSON de siniestros (reflejados en api.models.ts del frontend). */
public final class ClaimDtos {
    private ClaimDtos() {}

    public record ClaimServiceRequest(@NotBlank @Size(max = 160) String name,
                                      @Size(max = 500) String details,
                                      Instant scheduledAt) {}

    public record CreateClaimRequest(@NotNull UUID patientId,
                                     @NotBlank @Size(max = 2000) String description,
                                     ClaimStatus status,
                                     ClaimPriority priority,
                                     @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                     @Size(max = 120) String location,
                                     @Size(max = 20) List<@Valid ClaimServiceRequest> services) {}

    /** Actualización parcial: solo se modifican los campos enviados. */
    public record UpdateClaimRequest(@Size(min = 1, max = 2000) String description,
                                     ClaimStatus status,
                                     ClaimPriority priority,
                                     @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                     @Size(max = 120) String location,
                                     @Size(max = 255) String note) {}

    public record ChangeStatusRequest(@NotNull ClaimStatus status, @Size(max = 255) String note) {}

    public record PatientBrief(UUID id, String firstName, String lastName) {}
    public record ClaimServiceBrief(UUID id, String name, String details, Instant scheduledAt) {}
    public record ClaimHistoryEntry(ClaimStatus fromStatus, ClaimStatus toStatus, String note, Instant changedAt) {}

    public record ClaimResponse(UUID id, String claimNumber, String description, ClaimStatus status,
                                ClaimPriority priority, BigDecimal amount, String location, PatientBrief patient,
                                List<ClaimServiceBrief> services, List<ClaimHistoryEntry> history,
                                Instant createdAt, Instant updatedAt) {
        static ClaimResponse from(ClaimEntity claim) {
            var patient = claim.getPatient();
            return new ClaimResponse(claim.getId(), claim.getClaimNumber(), claim.getDescription(), claim.getStatus(),
                    claim.getPriority(), claim.getAmount(), claim.getLocation(),
                    new PatientBrief(patient.getId(), patient.getFirstName(), patient.getLastName()),
                    claim.getServices().stream()
                            .map(s -> new ClaimServiceBrief(s.getId(), s.getName(), s.getDetails(), s.getScheduledAt()))
                            .toList(),
                    claim.getHistory().stream()
                            .map(h -> new ClaimHistoryEntry(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt()))
                            .toList(),
                    claim.getCreatedAt(), claim.getUpdatedAt());
        }
    }
}
