package com.erp.insurance.retention;

import com.erp.insurance.retention.RetentionDtos.RetentionDecision;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionPolicyTest {
    private final RetentionPolicy policy = new RetentionPolicy(12, 7);
    private final LocalDate today = LocalDate.of(2026, 11, 14);

    @Test
    void keepsRecordsInUseOrWithRecentActivity() {
        assertThat(policy.evaluate(true, LocalDate.of(2015, 1, 1), null, today).decision()).isEqualTo(RetentionDecision.KEEP);
        assertThat(policy.evaluate(false, LocalDate.of(2026, 3, 1), null, today).decision()).isEqualTo(RetentionDecision.KEEP);
        assertThat(policy.evaluate(false, LocalDate.of(2027, 1, 1), null, today).inactiveMonths()).isZero();
    }

    @Test
    void archivesAfterTwelveMonthsAndDeletesAfterRetentionPeriod() {
        var archive = policy.evaluate(false, LocalDate.of(2024, 12, 31), null, today);
        assertThat(archive.decision()).isEqualTo(RetentionDecision.ARCHIVE);
        assertThat(archive.inactiveMonths()).isEqualTo(22);
        assertThat(archive.reason()).contains("22 meses");

        var delete = policy.evaluate(false, LocalDate.of(2017, 12, 31), null, today);
        assertThat(delete.decision()).isEqualTo(RetentionDecision.DELETE);
        assertThat(delete.reason()).contains("7 años");
    }

    @Test
    void legalHoldWinsOverArchiveAndDelete() {
        var hold = policy.evaluate(false, LocalDate.of(2010, 1, 1), "Siniestro CL-1 en trámite", today);
        assertThat(hold.decision()).isEqualTo(RetentionDecision.LEGAL_HOLD);
        assertThat(hold.reason()).isEqualTo("Siniestro CL-1 en trámite");
    }
}
