package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;

import java.time.Period;
import java.util.stream.Stream;

public final class BirthDateRule implements DetectionRule {
    static final int MAX_AGE = 120;

    @Override
    public String id() {
        return "birth-date";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        return dataset.patients().stream()
                .filter(patient -> patient.dateOfBirth() != null)
                .filter(patient -> patient.dateOfBirth().isAfter(dataset.referenceDate())
                        || Period.between(patient.dateOfBirth(), dataset.referenceDate()).getYears() > MAX_AGE)
                .map(patient -> Finding.builder(id())
                        .type(FindingType.OTHER)
                        .severity(Severity.RED)
                        .source("ACA")
                        .confidence(98)
                        .patient(patient)
                        .title("Fecha de nacimiento imposible")
                        .description("La fecha de nacimiento de " + patient.fullName() + " está fuera del rango válido.")
                        .comparison("Fecha de nacimiento", patient.dateOfBirth().toString(), "-")
                        .evidence("Debe ser anterior a " + dataset.referenceDate() + " y con edad máxima de " + MAX_AGE + " años")
                        .build());
    }
}
