package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AutoCorrectionRuleTest {
    @ParameterizedTest(name = "{0}.{1}={2} -> {3}")
    @CsvSource({
            "PATIENT, email,         a@b.test,  true",
            "PATIENT, phone,         305-1234,  true",
            "POLICY,  premiumAmount, 428.16,    true",
            "PATIENT, premiumAmount, 428.16,    false",
            "POLICY,  email,         a@b.test,  false",
            "CLAIM,   amount,        10,        false",
            "PATIENT, firstName,     Ana,       false",
            "PATIENT, email,         '',        false",
            "PATIENT, '',            a@b.test,  false",
    })
    void allowlist(AffectedRecordType type, String field, String value, boolean expected) {
        DataAnomalyEntity anomaly = new DataAnomalyEntity(AnomalyType.OTHER, AnomalySeverity.YELLOW, "t", "d",
                type, UUID.randomUUID(), null).withSuggestion(field, value);

        assertThat(AuditService.isAutoCorrectable(anomaly)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PATIENT", "POLICY", "CLAIM"})
    void withoutSuggestionIsNeverAutoCorrectable(AffectedRecordType type) {
        DataAnomalyEntity anomaly = new DataAnomalyEntity(AnomalyType.DUPLICATE, AnomalySeverity.RED, "t", "d",
                type, UUID.randomUUID(), null);

        assertThat(AuditService.isAutoCorrectable(anomaly)).isFalse();
    }
}
