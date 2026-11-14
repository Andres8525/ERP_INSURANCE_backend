package com.erp.anomaly;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyEngineApplicationTest {
    @Test
    void portDefaultsTo9090AndRejectsInvalidValues() {
        assertThat(AnomalyEngineApplication.port(null)).isEqualTo(9090);
        assertThat(AnomalyEngineApplication.port(" 7070 ")).isEqualTo(7070);
        assertThatThrownBy(() -> AnomalyEngineApplication.port("70000")).isInstanceOf(IllegalArgumentException.class);
    }
}
