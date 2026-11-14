package com.erp.insurance.clients;

import com.erp.insurance.clients.ClientDtos.ClientPlan;
import com.erp.insurance.clients.ClientDtos.ClientSummary;
import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import com.erp.insurance.policies.PolicyEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ClientService {
    private static final Comparator<PolicyEntity> MOST_RECENT =
            Comparator.comparing(PolicyEntity::getCoverageStart, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final PatientRepository patients;
    private final Clock clock;

    public ClientService(PatientRepository patients) {
        this.patients = patients;
        this.clock = Clock.systemUTC();
    }

    public record ClientRecord(PatientEntity patient, List<PolicyEntity> policies, ClientSummary summary) {}

    @Transactional(readOnly = true)
    public List<ClientSummary> list(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        return patients.findAll().stream()
                .map(this::summarize)
                .filter(summary -> needle.isEmpty() || searchableText(summary).contains(needle))
                .sorted(Comparator.comparing(ClientSummary::fullName, collator))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientRecord get(UUID id) {
        PatientEntity patient = patients.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        List<PolicyEntity> policies = patient.getPolicies().stream().sorted(MOST_RECENT.reversed()).toList();
        return new ClientRecord(patient, policies, summarize(patient));
    }

    ClientSummary summarize(PatientEntity patient) {
        LocalDate today = LocalDate.now(clock);
        Optional<PolicyEntity> current = currentPlan(patient.getPolicies(), today);
        ClientPlan plan = current.map(ClientPlan::from).orElse(null);
        String fullName = patient.getFirstName() + " " + patient.getLastName();
        return new ClientSummary(patient.getId(), patient.getCrmId(), fullName, patient.getEmail(), patient.getPhone(),
                patient.getCity(), age(patient.getDateOfBirth(), today), patient.getAccountStatus(),
                patient.getAcaEligibilityStatus(), plan, patient.getPolicies().size(),
                describe(plan, patient.getCity(), patient.getAcaEligibilityStatus()));
    }

    static Optional<PolicyEntity> currentPlan(List<PolicyEntity> policies, LocalDate today) {
        Optional<PolicyEntity> inForce = policies.stream()
                .filter(policy -> policy.getStatus() != PolicyStatus.TERMINATED)
                .filter(policy -> policy.getCoverageEnd() == null || !policy.getCoverageEnd().isBefore(today))
                .max(Comparator.comparing((PolicyEntity policy) -> policy.getStatus() == PolicyStatus.ACTIVE)
                        .thenComparing(MOST_RECENT));
        return inForce.isPresent() ? inForce : policies.stream().max(MOST_RECENT);
    }

    static String describe(ClientPlan plan, String city, AcaEligibilityStatus eligibility) {
        String coverage;
        if (plan == null) {
            coverage = "Sin plan médico registrado";
        } else {
            String name = plan.planName() == null || plan.planName().isBlank() ? "Plan" : plan.planName();
            String premium = plan.zeroPremium()
                    ? "prima $0 por subsidio ACA"
                    : "prima " + money(plan.premiumAmount()) + "/mes";
            String state = plan.status() == PolicyStatus.TERMINATED ? " (terminado)" : "";
            coverage = name + " de " + plan.insurer() + state + ", " + premium;
        }
        String place = city == null || city.isBlank() ? "ciudad no registrada" : city;
        return coverage + " · " + place + " · " + eligibilityLabel(eligibility);
    }

    static String eligibilityLabel(AcaEligibilityStatus status) {
        return switch (status) {
            case ELIGIBLE -> "Elegible ACA";
            case PENDING -> "Elegibilidad en verificación";
            case INELIGIBLE -> "No elegible ACA";
            case UNKNOWN -> "Elegibilidad sin evaluar";
        };
    }

    static String money(BigDecimal amount) {
        if (amount == null) return "-";
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    static Integer age(String dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) return null;
        try {
            return Period.between(LocalDate.parse(dateOfBirth), today).getYears();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String searchableText(ClientSummary summary) {
        Stream<String> values = Stream.of(summary.crmId(), summary.fullName(), summary.email(), summary.city(),
                summary.currentPlan() == null ? null : summary.currentPlan().insurer(),
                summary.currentPlan() == null ? null : summary.currentPlan().planName());
        return String.join(" ", values.filter(value -> value != null).toList()).toLowerCase(Locale.ROOT);
    }
}
