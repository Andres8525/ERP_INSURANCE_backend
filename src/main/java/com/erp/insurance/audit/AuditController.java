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

    public AuditController(AuditService audit) { this.audit = audit; }

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

    @Operation(summary = "Resolver anomalías en lote", description = "ARCHIVAR_DEFINITIVO o AUTOCORREGIR hasta 100 anomalías abiertas en una sola transacción; si una falla, se revierte todo el lote.")
    @PostMapping("/resolve-batch")
    public ResolveBatchResponse resolveBatch(@Valid @RequestBody ResolveBatchRequest request,
                                              JwtAuthenticationToken authentication) {
        return audit.resolveBatch(request, UUID.fromString(authentication.getToken().getSubject()));
    }
}