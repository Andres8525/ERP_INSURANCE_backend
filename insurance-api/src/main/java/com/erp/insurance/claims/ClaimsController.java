package com.erp.insurance.claims;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.erp.insurance.domain.DomainEnums.ClaimPriority;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.erp.insurance.claims.ClaimDtos.*;

@RestController
@Tag(name = "Siniestros", description = "CRUD de siniestros y pipeline Kanban")
@RequestMapping("/api/claims")
public class ClaimsController {
    private final ClaimsService claims;

    public ClaimsController(ClaimsService claims) {
        this.claims = claims;
    }

    @Operation(summary = "Pipeline Kanban", description = "Siniestros agrupados por OPEN, IN_REVIEW, APPROVED y REJECTED.")
    @GetMapping("/pipeline")
    public Map<ClaimStatus, List<ClaimResponse>> pipeline() {
        return claims.pipeline();
    }

    @GetMapping
    public List<ClaimResponse> list(@RequestParam(required = false) ClaimStatus status,
                                    @RequestParam(required = false) UUID patientId,
                                    @RequestParam(required = false) ClaimPriority priority) {
        return claims.list(status, patientId, priority);
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable UUID id) {
        return claims.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse create(@Valid @RequestBody CreateClaimRequest request) {
        return claims.create(request);
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ClaimResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClaimRequest request) {
        return claims.update(id, request);
    }

    @Operation(summary = "Cambiar estado", description = "Mueve el siniestro de columna y registra el cambio en su historial.")
    @PatchMapping("/{id}/status")
    public ClaimResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return claims.changeStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        claims.delete(id);
    }
}
