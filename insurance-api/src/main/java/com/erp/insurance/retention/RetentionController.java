package com.erp.insurance.retention;

import com.erp.insurance.retention.RetentionDtos.RetentionProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Retención y nube", description = "Perfilado de expedientes obsoletos para archivar o eliminar (ADMIN/AUDITOR)")
@RequestMapping("/api/audit/retention")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class RetentionController {
    private final RetentionService retention;

    public RetentionController(RetentionService retention) {
        this.retention = retention;
    }

    @Operation(summary = "Perfil de retención",
            description = "Clasifica pólizas, siniestros y expedientes en mantener, archivar, eliminar o retención legal "
                    + "según la política configurada, y estima el ahorro de almacenamiento en la nube.")
    @GetMapping
    public RetentionProfile profile() {
        return retention.profile();
    }
}
