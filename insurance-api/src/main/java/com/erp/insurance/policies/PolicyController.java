package com.erp.insurance.policies;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.erp.insurance.policies.PolicyDtos.*;

@RestController
@Tag(name = "Pólizas", description = "Pólizas vinculadas a un paciente")
@RequestMapping("/api/patients/{patientId}/policies")
public class PolicyController {
    private static final UUID NO_POLICY = new UUID(0L, 0L);
    private final PolicyRepository policies;
    private final PatientRepository patients;

    public PolicyController(PolicyRepository policies, PatientRepository patients) {
        this.policies = policies;
        this.patients = patients;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PolicyResponse> list(@PathVariable UUID patientId) {
        requirePatient(patientId);
        return policies.findByPatientIdOrderByCoverageStartDesc(patientId).stream().map(PolicyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PolicyResponse create(@PathVariable UUID patientId, @Valid @RequestBody CreatePolicyRequest request) {
        if (request.coverageEnd().isBefore(request.coverageStart())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "coverageEnd must be on or after coverageStart");
        }
        PatientEntity patient = requirePatient(patientId);
        PolicyStatus status = request.status() == null ? PolicyStatus.PENDING : request.status();
        rejectOverlap(patientId, request.coverageStart(), request.coverageEnd(), null, status);
        String number = request.policyNumber() == null || request.policyNumber().isBlank()
                ? "POL-" + ThreadLocalRandom.current().nextInt(100_000, 999_999)
                : request.policyNumber().trim();
        PolicyEntity policy = new PolicyEntity(patient, number, request.insurer().trim(),
                request.planName() == null ? null : request.planName().trim(), request.coverageStart(),
                request.coverageEnd(), request.premiumAmount(),
                status);
        return PolicyResponse.from(policies.save(policy));
    }

    @RequestMapping(value = "/{policyId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Transactional
    public PolicyResponse update(@PathVariable UUID patientId, @PathVariable UUID policyId,
                                 @Valid @RequestBody UpdatePolicyRequest request) {
        PolicyEntity policy = requirePolicy(patientId, policyId);
        if (request.insurer() != null) policy.setInsurer(request.insurer().trim());
        if (request.planName() != null) policy.setPlanName(request.planName().isBlank() ? null : request.planName().trim());
        if (request.coverageStart() != null) policy.setCoverageStart(request.coverageStart());
        if (request.coverageEnd() != null) policy.setCoverageEnd(request.coverageEnd());
        if (request.premiumAmount() != null) policy.setPremiumAmount(request.premiumAmount());
        if (request.status() != null) policy.setStatus(request.status());
        if (policy.getCoverageEnd().isBefore(policy.getCoverageStart())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "coverageEnd must be on or after coverageStart");
        }
        rejectOverlap(patientId, policy.getCoverageStart(), policy.getCoverageEnd(), policy.getId(), policy.getStatus());
        return PolicyResponse.from(policies.save(policy));
    }

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID patientId, @PathVariable UUID policyId) {
        policies.delete(requirePolicy(patientId, policyId));
    }

    private void rejectOverlap(UUID patientId, LocalDate start, LocalDate end, UUID excludeId, PolicyStatus status) {
        if (status == PolicyStatus.TERMINATED) return;
        UUID exclude = excludeId == null ? NO_POLICY : excludeId;
        if (policies.existsOverlapping(patientId, start, end, exclude, PolicyStatus.TERMINATED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The patient already has a policy whose coverage overlaps " + start + " - " + end);
        }
    }

    private PolicyEntity requirePolicy(UUID patientId, UUID policyId) {
        return policies.findById(policyId)
                .filter(policy -> policy.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found for this patient"));
    }

    private PatientEntity requirePatient(UUID patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }
}
