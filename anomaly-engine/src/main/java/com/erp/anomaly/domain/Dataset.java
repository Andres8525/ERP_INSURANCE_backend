package com.erp.anomaly.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Dataset(List<PatientRecord> patients, List<PolicyRecord> policies, LocalDate referenceDate) {
    public Dataset {
        patients = List.copyOf(patients);
        policies = List.copyOf(policies);
        Objects.requireNonNull(referenceDate, "referenceDate");
    }

    public int size() {
        return patients.size() + policies.size();
    }

    public Map<String, PatientRecord> patientsById() {
        return patients.stream().collect(Collectors.toMap(PatientRecord::id, Function.identity(), (a, b) -> a));
    }
}
