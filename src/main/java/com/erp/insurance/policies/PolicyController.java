package com.erp.insurance.policies;

import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.erp.insurance.policies.PolicyDtos.*;

/** Pólizas vinculadas a un paciente (paso 3 del stepper de afiliación y perfil del paciente). */
@RestController
@RequestMapping("/api/patients/{patientId}/policies")
public class PolicyController {
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
        String number = request.policyNumber() == null || request.policyNumber().isBlank()
                ? "POL-" + ThreadLocalRandom.current().nextInt(100_000, 999_999)
                : request.policyNumber().trim();
        PolicyEntity policy = new PolicyEntity(patient, number, request.insurer().trim(),
                request.planName() == null ? null : request.planName().trim(), request.coverageStart(),
                request.coverageEnd(), request.premiumAmount(),
                request.status() == null ? PolicyStatus.PENDING : request.status());
        return PolicyResponse.from(policies.save(policy));
    }

    private PatientEntity requirePatient(UUID patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }
}
