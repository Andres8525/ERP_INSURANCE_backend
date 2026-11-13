package com.erp.insurance.policies;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {
    List<PolicyEntity> findByPatientIdOrderByCoverageStartDesc(UUID patientId);
}
