package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimPriority;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.erp.insurance.claims.ClaimDtos.*;

@Service
public class ClaimsService {
    private final ClaimRepository claims;
    private final PatientRepository patients;

    public ClaimsService(ClaimRepository claims, PatientRepository patients) {
        this.claims = claims;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public Map<ClaimStatus, List<ClaimResponse>> pipeline() {
        Map<ClaimStatus, List<ClaimResponse>> pipeline = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            pipeline.put(status, claims.findByStatusOrderByUpdatedAtDesc(status).stream().map(ClaimResponse::from).toList());
        }
        return pipeline;
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> list(ClaimStatus status, UUID patientId) {
        List<ClaimEntity> result;
        if (patientId != null) {
            result = claims.findByPatientIdOrderByUpdatedAtDesc(patientId).stream()
                    .filter(claim -> status == null || claim.getStatus() == status).toList();
        } else if (status != null) {
            result = claims.findByStatusOrderByUpdatedAtDesc(status);
        } else {
            result = claims.findAllByOrderByUpdatedAtDesc();
        }
        return result.stream().map(ClaimResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(UUID id) {
        return ClaimResponse.from(require(id));
    }

    @Transactional
    public ClaimResponse create(CreateClaimRequest request) {
        PatientEntity patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Patient not found"));
        ClaimEntity claim = new ClaimEntity(nextClaimNumber(), patient, request.description().trim());
        claim.setPriority(request.priority() == null ? ClaimPriority.NORMAL : request.priority());
        claim.setAmount(request.amount());
        claim.setLocation(blankToNull(request.location()));
        if (request.services() != null) {
            request.services().forEach(s -> claim.addService(s.name().trim(), blankToNull(s.details()), s.scheduledAt()));
        }
        claim.changeStatus(ClaimStatus.OPEN, "Siniestro registrado");
        if (request.status() != null && request.status() != ClaimStatus.OPEN) {
            claim.changeStatus(request.status(), null);
        }
        return ClaimResponse.from(claims.save(claim));
    }

    @Transactional
    public ClaimResponse update(UUID id, UpdateClaimRequest request) {
        ClaimEntity claim = require(id);
        if (request.description() != null) claim.setDescription(request.description().trim());
        if (request.priority() != null) claim.setPriority(request.priority());
        if (request.amount() != null) claim.setAmount(request.amount());
        if (request.location() != null) claim.setLocation(blankToNull(request.location()));
        if (request.status() != null) claim.changeStatus(request.status(), blankToNull(request.note()));
        claim.touch();
        return ClaimResponse.from(claims.save(claim));
    }

    @Transactional
    public ClaimResponse changeStatus(UUID id, ChangeStatusRequest request) {
        ClaimEntity claim = require(id);
        claim.changeStatus(request.status(), blankToNull(request.note()));
        return ClaimResponse.from(claims.save(claim));
    }

    @Transactional
    public void delete(UUID id) {
        claims.delete(require(id));
    }

    private ClaimEntity require(UUID id) {
        return claims.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private String nextClaimNumber() {
        String candidate;
        do {
            candidate = "CL-" + ThreadLocalRandom.current().nextInt(10_000, 99_999);
        } while (claims.existsByClaimNumber(candidate));
        return candidate;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
