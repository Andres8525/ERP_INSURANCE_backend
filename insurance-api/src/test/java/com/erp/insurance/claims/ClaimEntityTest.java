package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import com.erp.insurance.patients.PatientEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimEntityTest {
    private ClaimEntity newClaim() {
        return new ClaimEntity("CL-10001", new PatientEntity(), "Consulta");
    }

    @Test
    void firstStatusHasNoPreviousState() {
        ClaimEntity claim = newClaim();
        claim.changeStatus(ClaimStatus.OPEN, "Siniestro registrado");

        assertThat(claim.getHistory()).hasSize(1);
        assertThat(claim.getHistory().get(0).getFromStatus()).isNull();
        assertThat(claim.getHistory().get(0).getToStatus()).isEqualTo(ClaimStatus.OPEN);
        assertThat(claim.getHistory().get(0).getNote()).isEqualTo("Siniestro registrado");
    }

    @Test
    void transitionsAreRecordedInOrder() {
        ClaimEntity claim = newClaim();
        claim.changeStatus(ClaimStatus.OPEN, null);
        claim.changeStatus(ClaimStatus.IN_REVIEW, null);
        claim.changeStatus(ClaimStatus.APPROVED, "Aprobado por auditoría");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getHistory()).extracting(ClaimStatusHistoryEntity::getToStatus)
                .containsExactly(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW, ClaimStatus.APPROVED);
        assertThat(claim.getHistory().get(2).getFromStatus()).isEqualTo(ClaimStatus.IN_REVIEW);
    }

    @Test
    void sameStatusIsIgnored() {
        ClaimEntity claim = newClaim();
        claim.changeStatus(ClaimStatus.OPEN, null);
        claim.changeStatus(ClaimStatus.OPEN, null);

        assertThat(claim.getHistory()).hasSize(1);
    }

    @Test
    void servicesAreLinkedToTheClaim() {
        ClaimEntity claim = newClaim();
        claim.addService("Transporte médico", "08:30", null);

        assertThat(claim.getServices()).singleElement()
                .satisfies(service -> assertThat(service.getName()).isEqualTo("Transporte médico"));
    }
}
