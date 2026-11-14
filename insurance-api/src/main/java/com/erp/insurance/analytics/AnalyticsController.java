package com.erp.insurance.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@Tag(name = "Analítica", description = "Conjuntos de datos de facturación para procesamiento en Web Workers")
@RequestMapping("/api/analytics")
@Validated
public class AnalyticsController {
    public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final BillingRecordGenerator generator;

    public AnalyticsController(BillingRecordGenerator generator) {
        this.generator = generator;
    }

    @Operation(summary = "Registros de facturación (CSV)",
            description = "Registros sintéticos tipo RIPS con diagnóstico CIE-10, servicio, valor, aseguradora, ciudad, edad y sexo. "
                    + "Mismo seed, mismos datos. Se transmite por streaming para no cargar el archivo completo en memoria.")
    @GetMapping(value = "/billing-records.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> billingRecords(
            @RequestParam(defaultValue = "50000") @Min(1) @Max(BillingRecordGenerator.MAX_RECORDS) int count,
            @RequestParam(defaultValue = "42") long seed) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        StreamingResponseBody body = output -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), 64 * 1024);
            generator.writeCsv(writer, count, seed, today);
        };
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("billing-" + count + "-" + seed + ".csv").build().toString())
                .body(body);
    }
}
