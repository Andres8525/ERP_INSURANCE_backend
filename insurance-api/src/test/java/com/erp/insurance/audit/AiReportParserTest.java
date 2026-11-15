package com.erp.insurance.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReportParserTest {
    @Test
    void limitsListsAndAcceptsPlainStringActions() {
        StringBuilder priorities = new StringBuilder();
        for (int i = 1; i <= 9; i++) priorities.append(i > 1 ? "," : "").append("{\"title\":\"Grupo ").append(i).append("\"}");
        var report = AiReportParser.parse("""
                Aquí está: {"summary":"**Estado** estable","riskLevel":"bajo","priorities":[%s],
                "actions":["Llamar al afiliado", {"title":""}],"prevention":"no es lista"} gracias""".formatted(priorities))
                .orElseThrow();

        assertThat(report.summary()).isEqualTo("Estado estable");
        assertThat(report.riskLevel()).isEqualTo("BAJO");
        assertThat(report.priorities()).hasSize(AiReportParser.MAX_PRIORITIES);
        assertThat(report.actions()).singleElement().satisfies(action -> assertThat(action.title()).isEqualTo("Llamar al afiliado"));
        assertThat(report.prevention()).isEmpty();
        assertThat(report.impact()).isNull();
    }

    @Test
    void rejectsContentWithoutSummaryOrJson() {
        assertThat(AiReportParser.parse("## Resumen\nsin json")).isEmpty();
        assertThat(AiReportParser.parse("{\"riskLevel\":\"ALTO\"}")).isEmpty();
        assertThat(AiReportParser.parse("{roto")).isEmpty();
    }

    @Test
    void normalizesSeverityAndRisk() {
        assertThat(AiReportParser.severity("RED")).isEqualTo("Crítica");
        assertThat(AiReportParser.severity("revisión")).isEqualTo("Revisión");
        assertThat(AiReportParser.severity("desconocida")).isNull();
        assertThat(AiReportParser.riskLevel("Crítico")).isEqualTo("ALTO");
        assertThat(AiReportParser.riskLevel(null)).isEqualTo("MEDIO");
        assertThat(AiReportParser.clean("a<br/>b <b>c</b>")).isEqualTo("a b c");
    }
}
