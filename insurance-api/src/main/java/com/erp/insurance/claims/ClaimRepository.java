package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {
    @EntityGraph(attributePaths = {"patient"})
    List<ClaimEntity> findAllByOrderByUpdatedAtDesc();
    @EntityGraph(attributePaths = {"patient"})
    List<ClaimEntity> findByStatusOrderByUpdatedAtDesc(ClaimStatus status);
    @EntityGraph(attributePaths = {"patient"})
    List<ClaimEntity> findByPatientIdOrderByUpdatedAtDesc(UUID patientId);
    boolean existsByPatientId(UUID patientId);
    boolean existsByClaimNumber(String claimNumber);
}
