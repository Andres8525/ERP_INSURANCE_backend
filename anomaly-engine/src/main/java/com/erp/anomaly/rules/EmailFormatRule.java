package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PatientRecord;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class EmailFormatRule implements DetectionRule {
    private static final List<String> TOP_LEVEL_DOMAINS = List.of("com", "net", "org", "edu", "gov", "us", "co");
    private static final Map<String, String> DOMAIN_TYPOS = Map.of(
            "gmial.com", "gmail.com",
            "gmal.com", "gmail.com",
            "gmail.con", "gmail.com",
            "hotmial.com", "hotmail.com",
            "hotmail.con", "hotmail.com",
            "yaho.com", "yahoo.com",
            "outlok.com", "outlook.com",
            "exmaple.test", "example.test");

    @Override
    public String id() {
        return "email-format";
    }

    @Override
    public Stream<Finding> evaluate(Dataset dataset) {
        return dataset.patients().stream()
                .filter(patient -> !TextNormalizer.isBlank(patient.email()))
                .flatMap(patient -> inspect(patient).stream());
    }

    private Optional<Finding> inspect(PatientRecord patient) {
        String email = patient.email().trim();
        Optional<String> suggestion = suggest(email);
        boolean valid = TextNormalizer.EMAIL.matcher(email).matches();
        if (valid && suggestion.isEmpty()) return Optional.empty();

        Finding.Builder builder = Finding.builder(id())
                .type(FindingType.OTHER)
                .severity(Severity.RED)
                .source("CRM")
                .patient(patient)
                .title(valid ? "Dominio de correo con error tipográfico" : "Correo electrónico inválido")
                .description("El correo de " + patient.fullName() + " no permite entregar avisos de la póliza.")
                .comparison("Correo electrónico", email, suggestion.orElse("-"))
                .evidence(valid ? "Dominio similar a uno conocido" : "No cumple el formato usuario@dominio.tld");
        suggestion.ifPresentOrElse(
                value -> builder.confidence(95).suggestion("email", value),
                () -> builder.confidence(85));
        return Optional.of(builder.build());
    }

    static Optional<String> suggest(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) return Optional.empty();
        String local = normalized.substring(0, at);
        String dotted = withTopLevelDot(normalized.substring(at + 1));
        String candidate = local + "@" + DOMAIN_TYPOS.getOrDefault(dotted, dotted);
        if (candidate.equals(normalized) || !TextNormalizer.EMAIL.matcher(candidate).matches()) return Optional.empty();
        return Optional.of(candidate);
    }

    private static String withTopLevelDot(String domain) {
        if (domain.contains(".")) return domain;
        return TOP_LEVEL_DOMAINS.stream()
                .filter(tld -> domain.length() > tld.length() + 1 && domain.endsWith(tld))
                .findFirst()
                .map(tld -> domain.substring(0, domain.length() - tld.length()) + "." + tld)
                .orElse(domain);
    }
}
