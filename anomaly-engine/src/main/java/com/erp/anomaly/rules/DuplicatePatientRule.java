package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PatientRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class DuplicatePatientRule implements DetectionRule {
    @Override
    public String id() {
        return "duplicate-patient";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        List<Finding> findings = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        Map<String, PatientRecord> byEmail = new HashMap<>();
        Map<String, PatientRecord> byIdentity = new HashMap<>();

        for (PatientRecord patient : dataset.patients()) {
            String email = TextNormalizer.isBlank(patient.email()) ? null : patient.email().trim().toLowerCase(Locale.ROOT);
            PatientRecord sameEmail = email == null ? null : byEmail.putIfAbsent(email, patient);
            if (sameEmail != null && reported.add(patient.id())) {
                findings.add(duplicate(patient, sameEmail, Severity.RED, 96, "Correo compartido: " + email));
                continue;
            }
            if (patient.dateOfBirth() == null) continue;
            String identity = TextNormalizer.identityKey(patient.fullName()) + "|" + patient.dateOfBirth();
            PatientRecord sameIdentity = byIdentity.putIfAbsent(identity, patient);
            if (sameIdentity != null && reported.add(patient.id())) {
                findings.add(duplicate(patient, sameIdentity, Severity.YELLOW, 90,
                        "Mismo nombre y fecha de nacimiento: " + patient.dateOfBirth()));
            }
        }
        return findings.stream();
    }

    private Finding duplicate(PatientRecord patient, PatientRecord original, Severity severity, int confidence,
                              String evidence) {
        return Finding.builder(id())
                .type(FindingType.DUPLICATE)
                .severity(severity)
                .source("CRM")
                .confidence(confidence)
                .patient(patient)
                .title("Posible cliente duplicado")
                .description(patient.fullName() + " parece ser el mismo asegurado que " + original.fullName() + ".")
                .comparison("Identidad del miembro", patient.fullName(), original.fullName())
                .evidence(evidence)
                .build();
    }
}
