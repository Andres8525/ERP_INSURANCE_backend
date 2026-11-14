package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.DomainTypes.FindingType;
import com.erp.anomaly.domain.DomainTypes.PolicyState;
import com.erp.anomaly.domain.DomainTypes.RecordType;
import com.erp.anomaly.domain.DomainTypes.Severity;
import com.erp.anomaly.domain.Finding;
import com.erp.anomaly.domain.PatientRecord;
import com.erp.anomaly.domain.PolicyRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final LocalDate DOB = LocalDate.of(1969, 3, 10);

    private static PatientRecord patient(String id, String first, String last, String email, String phone) {
        return new PatientRecord(id, first, last, email, phone, DOB);
    }

    private static PolicyRecord policy(String id, String patientId, LocalDate start, LocalDate end, String premium,
                                       PolicyState state) {
        return new PolicyRecord(id, patientId, "Ambetter", "Silver ACA", start, end,
                premium == null ? null : new BigDecimal(premium), state);
    }

    private static List<Finding> run(DetectionRule rule, List<PatientRecord> patients, List<PolicyRecord> policies) {
        return rule.evaluate(new Dataset(patients, policies, TODAY)).toList();
    }

    @Test
    void emailWithoutDotBeforeTopLevelDomainIsFlaggedWithAutoCorrection() {
        List<Finding> findings = run(new EmailFormatRule(),
                List.of(patient("p1", "Piedad", "Garcia", "piedad@gmailcom", null)), List.of());

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.RED);
            assertThat(finding.suggestedField()).isEqualTo("email");
            assertThat(finding.suggestedValue()).isEqualTo("piedad@gmail.com");
            assertThat(finding.title()).isEqualTo("Correo electrónico inválido");
        });
    }

    @Test
    void emailWithKnownTypoIsFlaggedEvenWhenSyntacticallyValid() {
        assertThat(EmailFormatRule.suggest("ana@gmial.com")).contains("ana@gmail.com");
        assertThat(EmailFormatRule.suggest("Ana@Gmail.com")).isEmpty();
        assertThat(EmailFormatRule.suggest("sin-arroba")).isEmpty();
    }

    @Test
    void validEmailsProduceNoFindings() {
        assertThat(run(new EmailFormatRule(),
                List.of(patient("p1", "Ana", "Ruiz", "ana.ruiz@correo.com", null)), List.of())).isEmpty();
    }

    @Test
    void phoneWithTenDigitsIsNormalizedAndOtherLengthsHaveNoSuggestion() {
        List<Finding> findings = run(new PhoneFormatRule(), List.of(
                patient("p1", "A", "B", "a@b.co", "3054508353"),
                patient("p2", "C", "D", "c@d.co", "305-555-01"),
                patient("p3", "E", "F", "e@f.co", "(305) 555-0100")), List.of());

        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).suggestedValue()).isEqualTo("(305) 450-8353");
        assertThat(findings.get(1).hasSuggestion()).isFalse();
        assertThat(PhoneFormatRule.normalize("+1 305 450 8353")).contains("(305) 450-8353");
    }

    @Test
    void birthDateInTheFutureIsImpossible() {
        PatientRecord future = new PatientRecord("p1", "A", "B", "a@b.co", null, TODAY.plusDays(1));
        PatientRecord ancient = new PatientRecord("p2", "C", "D", "c@d.co", null, TODAY.minusYears(130));
        PatientRecord normal = new PatientRecord("p3", "E", "F", "e@f.co", null, DOB);

        assertThat(run(new BirthDateRule(), List.of(future, ancient, normal), List.of()))
                .extracting(Finding::recordId).containsExactly("p1", "p2");
    }

    @Test
    void duplicatesAreDetectedByEmailAndByNormalizedIdentity() {
        List<Finding> findings = run(new DuplicatePatientRule(), List.of(
                patient("p1", "Lucía", "Martínez", "lucia@correo.com", null),
                patient("p2", "Lucia", "Martinez", "lucia.m@correo.com", null),
                patient("p3", "Otra", "Persona", "LUCIA@correo.com", null)), List.of());

        assertThat(findings).extracting(Finding::recordId).containsExactlyInAnyOrder("p2", "p3");
        assertThat(findings).allMatch(finding -> finding.type() == FindingType.DUPLICATE);
        assertThat(findings).filteredOn(finding -> finding.recordId().equals("p3"))
                .singleElement().extracting(Finding::severity).isEqualTo(Severity.RED);
    }

    @Test
    void overlappingPoliciesInForceAreFlaggedOncePerPolicy() {
        LocalDate jan = LocalDate.of(2026, 1, 1);
        List<Finding> findings = run(new PolicyOverlapRule(), List.of(), List.of(
                policy("a", "p1", jan, jan.plusMonths(11), "400", PolicyState.ACTIVE),
                policy("b", "p1", jan.plusMonths(1), jan.plusMonths(11), "420", PolicyState.PENDING),
                policy("c", "p1", jan.plusMonths(2), jan.plusMonths(11), "420", PolicyState.TERMINATED),
                policy("d", "p2", jan, jan.plusMonths(11), "300", PolicyState.ACTIVE)));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.recordId()).isEqualTo("b");
            assertThat(finding.recordType()).isEqualTo(RecordType.POLICY);
            assertThat(finding.patientId()).isEqualTo("p1");
        });
    }

    @Test
    void activePolicyPastItsEndDateIsObsolete() {
        List<Finding> findings = run(new ExpiredActivePolicyRule(), List.of(), List.of(
                policy("old", "p1", TODAY.minusYears(1), TODAY.minusDays(1), "400", PolicyState.ACTIVE),
                policy("ok", "p1", TODAY.minusDays(10), TODAY.plusDays(10), "400", PolicyState.ACTIVE)));

        assertThat(findings).singleElement().extracting(Finding::type).isEqualTo(FindingType.OBSOLETE_FILE);
    }

    @Test
    void premiumsOutsideTheExpectedRangeAreFlagged() {
        List<Finding> findings = run(new PremiumRangeRule(), List.of(), List.of(
                policy("zero", "p1", TODAY, TODAY.plusMonths(6), "0", PolicyState.PENDING),
                policy("high", "p1", TODAY, TODAY.plusMonths(6), "4999.99", PolicyState.ACTIVE),
                policy("ok", "p1", TODAY, TODAY.plusMonths(6), "482.16", PolicyState.ACTIVE),
                policy("closed", "p1", TODAY, TODAY.plusMonths(6), null, PolicyState.TERMINATED)));

        assertThat(findings).extracting(Finding::recordId, Finding::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("zero", Severity.RED),
                        org.assertj.core.groups.Tuple.tuple("high", Severity.YELLOW));
    }
}
