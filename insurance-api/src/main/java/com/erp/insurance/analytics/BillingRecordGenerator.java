package com.erp.insurance.analytics;

import com.erp.insurance.catalogs.CatalogDtos.Icd10Entry;
import com.erp.insurance.catalogs.CatalogService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;

@Component
public class BillingRecordGenerator {
    public static final String HEADER = "id,patientId,date,cie10,service,amount,insurer,city,age,sex";
    public static final int MAX_RECORDS = 200_000;

    static final String[] SERVICES = {"CONSULTA", "PROCEDIMIENTO", "MEDICAMENTO", "URGENCIA", "HOSPITALIZACION"};
    private static final double[] SERVICE_WEIGHTS = {0.46, 0.18, 0.22, 0.10, 0.04};
    private static final double[] BASE_AMOUNT = {85, 420, 60, 650, 3800};
    private static final String[] INSURERS = {"Ambetter", "Oscar Health", "Florida Blue", "Molina Healthcare", "Aetna CVS Health"};
    private static final String[] CITIES = {"Miami", "Orlando", "Tampa", "Jacksonville", "Fort Lauderdale", "Boston"};

    private final List<Icd10Entry> diagnoses;

    public BillingRecordGenerator(CatalogService catalogs) {
        this.diagnoses = catalogs.allIcd10();
    }

    public void writeCsv(Writer writer, int count, long seed, LocalDate until) throws IOException {
        SplittableRandom random = new SplittableRandom(seed);
        int patients = Math.max(1, count / 4);
        writer.write(HEADER);
        writer.write('\n');
        StringBuilder line = new StringBuilder(96);
        for (int i = 0; i < count; i++) {
            int service = pick(random);
            Icd10Entry diagnosis = diagnoses.get(skewedIndex(random, diagnoses.size()));
            double amount = BASE_AMOUNT[service] * (0.55 + random.nextDouble() * 1.35);
            line.setLength(0);
            line.append("R").append(i + 1).append(',')
                    .append("P").append(1 + random.nextInt(patients)).append(',')
                    .append(until.minusDays(random.nextInt(365))).append(',')
                    .append(i % 997 == 0 ? "X0" : diagnosis.code()).append(',')
                    .append(SERVICES[service]).append(',')
                    .append(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()).append(',')
                    .append(INSURERS[random.nextInt(INSURERS.length)]).append(',')
                    .append(CITIES[random.nextInt(CITIES.length)]).append(',')
                    .append(random.nextInt(1, 90)).append(',')
                    .append(random.nextBoolean() ? 'F' : 'M')
                    .append('\n');
            writer.append(line);
        }
        writer.flush();
    }

    private static int pick(SplittableRandom random) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < SERVICE_WEIGHTS.length; i++) {
            cumulative += SERVICE_WEIGHTS[i];
            if (roll < cumulative) return i;
        }
        return SERVICE_WEIGHTS.length - 1;
    }

    private static int skewedIndex(SplittableRandom random, int size) {
        double value = random.nextDouble();
        return Math.min(size - 1, (int) (value * value * size));
    }
}
