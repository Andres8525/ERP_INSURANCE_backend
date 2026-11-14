package com.erp.insurance.clients;

import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.policies.PolicyEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ClientDtos {
    private ClientDtos() {}

    public record ClientPlan(UUID policyId, String policyNumber, String insurer, String planName,
                             BigDecimal premiumAmount, boolean zeroPremium, LocalDate coverageStart,
                             LocalDate coverageEnd, PolicyStatus status) {
        static ClientPlan from(PolicyEntity policy) {
            BigDecimal premium = policy.getPremiumAmount();
            return new ClientPlan(policy.getId(), policy.getPolicyNumber(), policy.getInsurer(), policy.getPlanName(),
                    premium, premium != null && premium.signum() == 0, policy.getCoverageStart(),
                    policy.getCoverageEnd(), policy.getStatus());
        }
    }

    public record ClientSummary(UUID id, String crmId, String fullName, String email, String phone, String city,
                                Integer age, AccountStatus accountStatus, AcaEligibilityStatus acaEligibilityStatus,
                                ClientPlan currentPlan, int policies, String description) {}
}
