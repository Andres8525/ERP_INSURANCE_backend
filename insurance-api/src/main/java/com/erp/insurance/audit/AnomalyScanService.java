package com.erp.insurance.audit;

import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.Finding;
import com.erp.insurance.integration.anomaly.AnomalyDetector;
import com.erp.insurance.integration.anomaly.AnomalyDetector.Detection;
import com.erp.insurance.integration.anomaly.SnapshotMapper;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import com.erp.insurance.policies.PolicyEntity;
import com.erp.insurance.policies.PolicyRepository;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.erp.insurance.audit.AuditDtos.ScanResponse;

@Service
public class AnomalyScanService {
    private final AnomalyDetector detector;
    private final DataAnomalyRepository anomalies;
    private final PatientRepository patients;
    private final PolicyRepository policies;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate writable;
    private final Clock clock;

    public AnomalyScanService(AnomalyDetector detector, DataAnomalyRepository anomalies, PatientRepository patients,
                              PolicyRepository policies, PlatformTransactionManager transactions) {
        this.detector = detector;
        this.anomalies = anomalies;
        this.patients = patients;
        this.policies = policies;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.writable = new TransactionTemplate(transactions);
        this.clock = Clock.systemUTC();
    }

    public ScanResponse scan() {
        long startedAt = System.nanoTime();
        String correlationId = UUID.randomUUID().toString();
        AnalyzeRequest request = Objects.requireNonNull(readOnly.execute(status ->
                SnapshotMapper.request(patients.findAll(), policies.findAll(), LocalDate.now(clock))));

        Detection detection;
        try {
            detection = detector.analyze(request, correlationId);
        } catch (StatusRuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Anomaly engine failed: " + exception.getStatus().getCode(), exception);
        }

        List<Finding> findings = detection.response().getFindingsList();
        int created = Objects.requireNonNull(writable.execute(status -> persist(findings)));
        return new ScanResponse(
                detection.response().getAnalyzedRecords(),
                findings.size(),
                created,
                findings.size() - created,
                detection.mode().name(),
                detection.response().getEngineVersion(),
                detection.response().getAppliedRulesList(),
                correlationId,
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private int persist(List<Finding> findings) {
        if (findings.isEmpty()) return 0;
        Set<String> known = anomalies.findAll().stream()
                .map(anomaly -> key(anomaly.getType().name(), anomaly.getAffectedRecordId().toString(),
                        anomaly.getFieldLabel(), anomaly.getCurrentValue()))
                .collect(Collectors.toCollection(HashSet::new));
        Map<UUID, PatientEntity> patientsById = patients.findAll().stream()
                .collect(Collectors.toMap(PatientEntity::getId, Function.identity()));

        List<DataAnomalyEntity> fresh = new ArrayList<>();
        Set<UUID> flaggedPolicies = new HashSet<>();
        for (Finding finding : findings) {
            String key = key(SnapshotMapper.type(finding).name(), finding.getRecordId(),
                    SnapshotMapper.blankToNull(finding.getFieldLabel()), SnapshotMapper.blankToNull(finding.getCurrentValue()));
            if (!known.add(key)) continue;
            UUID recordId = UUID.fromString(finding.getRecordId());
            PatientEntity patient = finding.getPatientId().isBlank()
                    ? null : patientsById.get(UUID.fromString(finding.getPatientId()));
            DataAnomalyEntity anomaly = new DataAnomalyEntity(SnapshotMapper.type(finding),
                    SnapshotMapper.severity(finding), finding.getTitle(), finding.getDescription(),
                    SnapshotMapper.recordType(finding), recordId, patient)
                    .withAnalysis(finding.getSource(), finding.getConfidence(),
                            SnapshotMapper.blankToNull(finding.getFieldLabel()),
                            SnapshotMapper.blankToNull(finding.getCurrentValue()),
                            SnapshotMapper.blankToNull(finding.getConflictingValue()),
                            SnapshotMapper.blankToNull(finding.getEvidence()));
            if (!finding.getSuggestedField().isBlank() && !finding.getSuggestedValue().isBlank()) {
                anomaly.withSuggestion(finding.getSuggestedField(), finding.getSuggestedValue());
            }
            if (anomaly.getAffectedRecordType() == com.erp.insurance.domain.DomainEnums.AffectedRecordType.POLICY) {
                flaggedPolicies.add(recordId);
            }
            fresh.add(anomaly);
        }
        anomalies.saveAll(fresh);
        if (!flaggedPolicies.isEmpty()) {
            List<PolicyEntity> affected = policies.findAllById(flaggedPolicies);
            affected.forEach(policy -> policy.setHasAnomalies(true));
            policies.saveAll(affected);
        }
        return fresh.size();
    }

    private static String key(String type, String recordId, String fieldLabel, String currentValue) {
        return type + "|" + recordId + "|" + fieldLabel + "|" + currentValue;
    }
}
