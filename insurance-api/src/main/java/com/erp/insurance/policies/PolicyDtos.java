package com.erp.insurance.policies;

import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PolicyDtos {
    private PolicyDtos() {}

    public record CreatePolicyRequest(
            @Size(max = 60) String policyNumber,
            @NotBlank @Size(max = 200) String insurer,
            @Size(max = 120) String planName,
            @NotNull LocalDate coverageStart,
            @NotNull LocalDate coverageEnd,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal premiumAmount,
            PolicyStatus status) {}

    public record UpdatePolicyRequest(
            @Size(min = 1, max = 200) String insurer,
            @Size(max = 120) String planName,
            LocalDate coverageStart,
            LocalDate coverageEnd,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal premiumAmount,
            PolicyStatus status) {}

    public record PolicyResponse(UUID id, UUID patientId, String policyNumber, String insurer, String planName,
                                 LocalDate coverageStart, LocalDate coverageEnd, BigDecimal premiumAmount,
                                 PolicyStatus status, boolean hasAnomalies, Instant createdAt) {
        public static PolicyResponse from(PolicyEntity policy) {
            return new PolicyResponse(policy.getId(), policy.getPatient().getId(), policy.getPolicyNumber(),
                    policy.getInsurer(), policy.getPlanName(), policy.getCoverageStart(), policy.getCoverageEnd(),
                    policy.getPremiumAmount(), policy.getStatus(), policy.isHasAnomalies(), policy.getCreatedAt());
        }
    }
}
