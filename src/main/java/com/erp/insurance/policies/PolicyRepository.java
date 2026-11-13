package com.erp.insurance.policies;

import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {
    List<PolicyEntity> findByPatientIdOrderByCoverageStartDesc(UUID patientId);

    /**
     * ¿Existe otra póliza no terminada del paciente cuyo periodo se cruza con [start, end]?
     * {@code excludeId} nunca es null (PostgreSQL no infiere el tipo de un UUID nulo).
     */
    @Query("""
            select count(p) > 0 from PolicyEntity p
            where p.patient.id = :patientId and p.status <> :terminated
              and p.id <> :excludeId
              and p.coverageStart <= :end and p.coverageEnd >= :start""")
    boolean existsOverlapping(@Param("patientId") UUID patientId, @Param("start") LocalDate start,
                              @Param("end") LocalDate end, @Param("excludeId") UUID excludeId,
                              @Param("terminated") PolicyStatus terminated);
}
