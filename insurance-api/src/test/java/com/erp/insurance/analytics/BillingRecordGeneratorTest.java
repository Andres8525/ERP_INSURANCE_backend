package com.erp.insurance.analytics;

import com.erp.insurance.catalogs.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BillingRecordGeneratorTest {
    private final BillingRecordGenerator generator = new BillingRecordGenerator(new CatalogService(new ObjectMapper()));

    @Test
    void recordsHaveValidFieldsAndKnownServices() throws Exception {
        StringWriter out = new StringWriter();
        LocalDate until = LocalDate.of(2026, 11, 14);
        generator.writeCsv(out, 2_000, 99, until);
        String[] lines = out.toString().split("\n");
        Set<String> services = Set.of(BillingRecordGenerator.SERVICES);

        assertThat(lines).hasSize(2_001);
        long invalidCodes = Arrays.stream(lines).skip(1).map(line -> line.split(",")).filter(cols -> {
            assertThat(services).contains(cols[4]);
            assertThat(new BigDecimal(cols[5])).isPositive();
            LocalDate date = LocalDate.parse(cols[2]);
            assertThat(date).isBetween(until.minusDays(365), until);
            assertThat(Integer.parseInt(cols[8])).isBetween(1, 89);
            assertThat(cols[9]).isIn("F", "M");
            return !cols[3].matches("^[A-Z]\\d{2}(\\.\\d{1,2})?$");
        }).count();
        assertThat(invalidCodes).as("códigos inválidos sembrados para probar la validación").isBetween(1L, 5L);
    }
}
