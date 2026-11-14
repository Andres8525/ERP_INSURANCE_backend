package com.erp.insurance.clients;

import com.erp.insurance.clients.ClientDtos.ClientPlan;
import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.CrmIds;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.policies.PolicyEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private static PolicyEntity policy(String plan, LocalDate start, LocalDate end, String premium, PolicyStatus status) {
        return new PolicyEntity(new PatientEntity(), "POL-" + plan, "Ambetter", plan, start, end, new BigDecimal(premium), status);
    }

    @Test
    void currentPlanPrefersActivePoliciesInForce() {
        PolicyEntity old = policy("Old", TODAY.minusYears(1), TODAY.minusDays(30), "300", PolicyStatus.ACTIVE);
        PolicyEntity pending = policy("Next", TODAY.plusDays(10), TODAY.plusYears(1), "250", PolicyStatus.PENDING);
        PolicyEntity active = policy("Now", TODAY.minusMonths(2), TODAY.plusMonths(2), "0.00", PolicyStatus.ACTIVE);

        assertThat(ClientService.currentPlan(List.of(old, pending, active), TODAY)).contains(active);
        assertThat(ClientService.currentPlan(List.of(old, pending), TODAY)).contains(pending);
        assertThat(ClientService.currentPlan(List.of(old), TODAY)).contains(old);
        assertThat(ClientService.currentPlan(List.of(), TODAY)).isEmpty();
    }

    @Test
    void descriptionSummarizesPlanPremiumCityAndEligibility() {
        ClientPlan zero = new ClientPlan(UUID.randomUUID(), "P-1", "Oscar Health", "Simple Silver EPO",
                new BigDecimal("0.00"), true, TODAY, TODAY.plusYears(1), PolicyStatus.ACTIVE);
        ClientPlan paid = new ClientPlan(UUID.randomUUID(), "P-2", "Ambetter", "Clear Silver 73",
                new BigDecimal("428.16"), false, TODAY, TODAY.plusYears(1), PolicyStatus.ACTIVE);

        assertThat(ClientService.describe(zero, "Miami", AcaEligibilityStatus.ELIGIBLE))
                .isEqualTo("Simple Silver EPO de Oscar Health, prima $0 por subsidio ACA · Miami · Elegible ACA");
        assertThat(ClientService.describe(paid, null, AcaEligibilityStatus.PENDING))
                .isEqualTo("Clear Silver 73 de Ambetter, prima $428.16/mes · ciudad no registrada · Elegibilidad en verificación");
        assertThat(ClientService.describe(null, "Tampa", AcaEligibilityStatus.UNKNOWN))
                .startsWith("Sin plan médico registrado · Tampa");
    }

    @Test
    void ageIsComputedFromTheIsoBirthDate() {
        assertThat(ClientService.age("1969-03-10", TODAY)).isEqualTo(57);
        assertThat(ClientService.age("no-date", TODAY)).isNull();
    }

    @Test
    void crmIdsAreReadableAndUnique() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 2_000; i++) generated.add(CrmIds.next());
        assertThat(generated).hasSize(2_000).allMatch(CrmIds::isValid);
        assertThat(generated).allMatch(id -> id.startsWith("CRMID-")).noneMatch(id -> id.substring(6).matches(".*[01IO].*"));
    }
}
