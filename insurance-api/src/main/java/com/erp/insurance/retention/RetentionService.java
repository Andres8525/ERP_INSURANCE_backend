package com.erp.insurance.retention;

import com.erp.insurance.audit.DataAnomalyEntity;
import com.erp.insurance.audit.DataAnomalyRepository;
import com.erp.insurance.claims.ClaimEntity;
import com.erp.insurance.claims.ClaimRepository;
import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import com.erp.insurance.policies.PolicyEntity;
import com.erp.insurance.policies.PolicyRepository;
import com.erp.insurance.retention.RetentionDtos.CategoryBreakdown;
import com.erp.insurance.retention.RetentionDtos.RecordCategory;
import com.erp.insurance.retention.RetentionDtos.RetentionCandidate;
import com.erp.insurance.retention.RetentionDtos.RetentionDecision;
import com.erp.insurance.retention.RetentionDtos.RetentionProfile;
import com.erp.insurance.retention.RetentionDtos.RetentionRules;
import com.erp.insurance.retention.RetentionDtos.RetentionSummary;
import com.erp.insurance.retention.RetentionPolicy.Verdict;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionService {
    private static final Comparator<RetentionCandidate> PRIORITY = Comparator
            .comparingInt((RetentionCandidate candidate) -> switch (candidate.decision()) {
                case DELETE -> 0;
                case ARCHIVE -> 1;
                case LEGAL_HOLD -> 2;
                case KEEP -> 3;
            })
            .thenComparing(Comparator.comparingInt(RetentionCandidate::inactiveMonths).reversed());

    private final PatientRepository patients;
    private final PolicyRepository policies;
    private final ClaimRepository claims;
    private final DataAnomalyRepository anomalies;
    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionService(PatientRepository patients, PolicyRepository policies, ClaimRepository claims,
                            DataAnomalyRepository anomalies, RetentionProperties properties) {
        this.patients = patients;
        this.policies = policies;
        this.claims = claims;
        this.anomalies = anomalies;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public RetentionProfile profile() {
        LocalDate today = LocalDate.now(clock);
        RetentionPolicy policy = new RetentionPolicy(properties.archiveAfterMonths(), properties.retentionYears());
        List<PolicyEntity> allPolicies = policies.findAll();
        List<ClaimEntity> allClaims = claims.findAll();
        Map<UUID, String> holds = holds(allClaims, anomalies.findAll());

        List<RetentionCandidate> records = new ArrayList<>();
        Map<UUID, Boolean> patientInUse = new HashMap<>();
        Map<UUID, LocalDate> patientActivity = new HashMap<>();

        for (PolicyEntity entity : allPolicies) {
            UUID patientId = entity.getPatient().getId();
            boolean inUse = entity.getStatus() != PolicyStatus.TERMINATED
                    && (entity.getCoverageEnd() == null || !entity.getCoverageEnd().isBefore(today));
            LocalDate lastActivity = entity.getCoverageEnd() == null ? today : entity.getCoverageEnd();
            track(patientInUse, patientActivity, patientId, inUse, lastActivity);
            Verdict verdict = policy.evaluate(inUse, lastActivity, holds.get(patientId), today);
            records.add(new RetentionCandidate(entity.getId(), RecordCategory.POLICY,
                    entity.getPolicyNumber() + " · " + entity.getInsurer(), entity.getPatient().getCrmId(), lastActivity,
                    verdict.inactiveMonths(), properties.policyDocumentMb(), verdict.decision(), verdict.reason()));
        }

        for (ClaimEntity entity : allClaims) {
            UUID patientId = entity.getPatient().getId();
            boolean inUse = entity.getStatus() == ClaimStatus.OPEN || entity.getStatus() == ClaimStatus.IN_REVIEW;
            LocalDate lastActivity = date(entity.getUpdatedAt());
            track(patientInUse, patientActivity, patientId, inUse, lastActivity);
            Verdict verdict = policy.evaluate(inUse, lastActivity, holds.get(patientId), today);
            double size = properties.claimDocumentMb() + entity.getServices().size() * properties.claimServiceMb();
            records.add(new RetentionCandidate(entity.getId(), RecordCategory.CLAIM, entity.getClaimNumber(),
                    entity.getPatient().getCrmId(), lastActivity, verdict.inactiveMonths(), round(size),
                    verdict.decision(), verdict.reason()));
        }

        for (PatientEntity entity : patients.findAll()) {
            boolean inUse = entity.getAccountStatus() != AccountStatus.INACTIVE
                    || patientInUse.getOrDefault(entity.getId(), false);
            LocalDate lastActivity = Stream.of(patientActivity.get(entity.getId()), date(entity.getUpdatedAt()))
                    .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(today);
            Verdict verdict = policy.evaluate(inUse, lastActivity, holds.get(entity.getId()), today);
            records.add(new RetentionCandidate(entity.getId(), RecordCategory.PATIENT_FILE,
                    "Expediente " + entity.getCrmId(), entity.getCrmId(), lastActivity, verdict.inactiveMonths(),
                    properties.patientFileMb(), verdict.decision(), verdict.reason()));
        }

        return new RetentionProfile(summary(records), new RetentionRules(properties.archiveAfterMonths(),
                properties.retentionYears(), properties.hotUsdPerGbMonth(), properties.archiveUsdPerGbMonth()),
                categories(records),
                records.stream().filter(record -> record.decision() != RetentionDecision.KEEP).sorted(PRIORITY).toList(),
                Instant.now(clock));
    }

    private static Map<UUID, String> holds(List<ClaimEntity> claims, List<DataAnomalyEntity> anomalies) {
        Map<UUID, String> holds = new HashMap<>();
        anomalies.stream()
                .filter(anomaly -> anomaly.getResolutionStatus() == AnomalyResolutionStatus.OPEN
                        && anomaly.getSeverity() == AnomalySeverity.RED && anomaly.getPatient() != null)
                .forEach(anomaly -> holds.put(anomaly.getPatient().getId(),
                        "Anomalía crítica abierta: conservar hasta resolverla"));
        claims.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.OPEN || claim.getStatus() == ClaimStatus.IN_REVIEW)
                .forEach(claim -> holds.put(claim.getPatient().getId(),
                        "Siniestro " + claim.getClaimNumber() + " en trámite: retención legal hasta su cierre"));
        return holds;
    }

    private static void track(Map<UUID, Boolean> inUse, Map<UUID, LocalDate> activity, UUID patientId,
                              boolean recordInUse, LocalDate lastActivity) {
        inUse.merge(patientId, recordInUse, Boolean::logicalOr);
        activity.merge(patientId, lastActivity, (left, right) -> left.isAfter(right) ? left : right);
    }

    private RetentionSummary summary(List<RetentionCandidate> records) {
        Map<RetentionDecision, Double> mb = new EnumMap<>(RetentionDecision.class);
        Map<RetentionDecision, Integer> count = new EnumMap<>(RetentionDecision.class);
        for (RetentionCandidate record : records) {
            mb.merge(record.decision(), record.sizeMb(), Double::sum);
            count.merge(record.decision(), 1, Integer::sum);
        }
        double totalGb = gb(mb.values().stream().mapToDouble(Double::doubleValue).sum());
        double archiveGb = gb(mb.getOrDefault(RetentionDecision.ARCHIVE, 0d));
        double deleteGb = gb(mb.getOrDefault(RetentionDecision.DELETE, 0d));
        double current = totalGb * properties.hotUsdPerGbMonth();
        double optimized = (totalGb - archiveGb - deleteGb) * properties.hotUsdPerGbMonth()
                + archiveGb * properties.archiveUsdPerGbMonth();
        double savings = current - optimized;
        double perRecordAnnual = records.isEmpty() ? 0 : savings / records.size() * 12;
        return new RetentionSummary(records.size(), count.getOrDefault(RetentionDecision.KEEP, 0),
                count.getOrDefault(RetentionDecision.ARCHIVE, 0), count.getOrDefault(RetentionDecision.DELETE, 0),
                count.getOrDefault(RetentionDecision.LEGAL_HOLD, 0), round(totalGb), round(archiveGb), round(deleteGb),
                cents(current), cents(optimized), cents(savings), current == 0 ? 0 : round(savings / current * 100),
                cents(perRecordAnnual * 10_000));
    }

    private static List<CategoryBreakdown> categories(List<RetentionCandidate> records) {
        List<CategoryBreakdown> result = new ArrayList<>();
        for (RecordCategory category : RecordCategory.values()) {
            List<RetentionCandidate> group = records.stream().filter(record -> record.category() == category).toList();
            result.add(new CategoryBreakdown(category, category.label(), group.size(),
                    round(group.stream().mapToDouble(RetentionCandidate::sizeMb).sum()),
                    countOf(group, RetentionDecision.KEEP), countOf(group, RetentionDecision.ARCHIVE),
                    countOf(group, RetentionDecision.DELETE), countOf(group, RetentionDecision.LEGAL_HOLD)));
        }
        return result;
    }

    private static int countOf(List<RetentionCandidate> group, RetentionDecision decision) {
        return (int) group.stream().filter(record -> record.decision() == decision).count();
    }

    private static LocalDate date(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static double gb(double mb) {
        return mb / 1024;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static double cents(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
