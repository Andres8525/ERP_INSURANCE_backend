package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PatientRecord;

import java.util.Optional;
import java.util.stream.Stream;

public final class PhoneFormatRule implements DetectionRule {
    @Override
    public String id() {
        return "phone-format";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        return dataset.patients().stream()
                .filter(patient -> !TextNormalizer.isBlank(patient.phone()))
                .flatMap(patient -> inspect(patient).stream());
    }

    private Optional<Finding> inspect(PatientRecord patient) {
        String phone = patient.phone().trim();
        Optional<String> normalized = normalize(phone);
        if (normalized.isPresent() && normalized.get().equals(phone)) return Optional.empty();

        Finding.Builder builder = Finding.builder(id())
                .type(FindingType.OTHER)
                .severity(Severity.YELLOW)
                .source("CRM")
                .patient(patient);
        normalized.ifPresentOrElse(
                value -> builder.title("Teléfono con formato no estándar")
                        .description("El teléfono de " + patient.fullName() + " no sigue el formato (NNN) NNN-NNNN.")
                        .comparison("Teléfono", phone, value)
                        .evidence("10 dígitos válidos de Estados Unidos")
                        .confidence(92)
                        .suggestion("phone", value),
                () -> builder.title("Teléfono con formato inválido")
                        .description("El teléfono de " + patient.fullName() + " no tiene 10 dígitos.")
                        .comparison("Teléfono", phone, "-")
                        .evidence("Se esperaban 10 dígitos y hay " + TextNormalizer.digits(phone).length())
                        .confidence(75));
        return Optional.of(builder.build());
    }

    public static Optional<String> normalize(String phone) {
        String digits = TextNormalizer.digits(phone);
        if (digits.length() == 11 && digits.startsWith("1")) digits = digits.substring(1);
        if (digits.length() != 10) return Optional.empty();
        return Optional.of("(" + digits.substring(0, 3) + ") " + digits.substring(3, 6) + "-" + digits.substring(6));
    }
}
