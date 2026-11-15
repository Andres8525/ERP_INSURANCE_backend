package com.erp.insurance.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.erp.insurance.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

import static com.erp.insurance.audit.AuditDtos.*;

@RestController
@Tag(name = "Auditoría IA", description = "Bandeja de anomalías y resolución masiva (ADMIN/AUDITOR)")
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
@Validated
public class AuditController {
    private final AuditService audit;
    private final AnomalyScanService scanner;
    private final AiInsightService ai;

    public AuditController(AuditService audit, AnomalyScanService scanner, AiInsightService ai) {
        this.audit = audit;
        this.scanner = scanner;
        this.ai = ai;
    }

    @Operation(summary = "Ejecutar análisis IA", description = "Envía pacientes y pólizas al microservicio anomaly-engine por gRPC y registra los hallazgos nuevos.")
    @PostMapping("/scan")
    public ScanResponse scan() {
        return scanner.scan();
    }

    @Operation(summary = "Listar anomalías", description = "Hallazgos del motor de IA (simulado), paginados y filtrables por severidad, estado y tipo.")
    @GetMapping("/anomalies")
    public PageResponse<AnomalyResponse> anomalies(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
                                           @RequestParam(required = false) AnomalySeverity severity,
                                           @RequestParam(required = false) AnomalyResolutionStatus resolutionStatus,
                                           @RequestParam(required = false) AnomalyType type) {
        return PageResponse.of(audit.list(page, size, severity, resolutionStatus, type));
    }

    @Operation(summary = "Resumen de la bandeja", description = "Abiertas, críticas (RED), en revisión (YELLOW) y resueltas este mes.")
    @GetMapping("/summary")
    public AuditSummaryResponse summary() {
        return audit.summary();
    }

    @Operation(summary = "Estado del asistente IA", description = "Indica si hay proveedor de IA configurado (AI_API_KEY), cuál es y qué modelo usa.")
    @GetMapping("/ai/status")
    public AiStatusResponse aiStatus() {
        return ai.status();
    }

    @Operation(summary = "Recomendaciones IA de la bandeja", description = "Envía al proveedor de IA (Groq, Grok, Gemini u OpenRouter) un resumen anonimizado de las anomalías abiertas y devuelve diagnóstico, prioridades y acciones. Si la bandeja no cambió responde desde caché (cached=true), salvo con refresh=true.")
    @PostMapping("/ai/insights")
    public AiInsightResponse aiInsights(@RequestParam(defaultValue = "false") boolean refresh) {
        return ai.insights(refresh);
    }

    @Operation(summary = "Explicar una anomalía con IA", description = "Explicación y pasos de corrección generados por la IA sin enviar datos personales.")
    @PostMapping("/anomalies/{id}/ai-explanation")
    public AiInsightResponse aiExplanation(@PathVariable UUID id) {
        return ai.explain(id);
    }

    @Operation(summary = "Resolver anomalías en lote", description = "ARCHIVAR_DEFINITIVO o AUTOCORREGIR hasta 100 anomalías abiertas en una sola transacción; si una falla, se revierte todo el lote.")
    @PostMapping("/resolve-batch")
    public ResolveBatchResponse resolveBatch(@Valid @RequestBody ResolveBatchRequest request,
                                              JwtAuthenticationToken authentication) {
        return audit.resolveBatch(request, UUID.fromString(authentication.getToken().getSubject()));
    }
}
