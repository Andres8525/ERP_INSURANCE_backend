package com.erp.insurance.claims;

import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.erp.insurance.claims.ClaimDtos.*;

@RestController
@RequestMapping("/api/claims")
public class ClaimsController {
    private final ClaimsService claims;

    public ClaimsController(ClaimsService claims) {
        this.claims = claims;
    }

    /** Siniestros agrupados por estado para el tablero Kanban. */
    @GetMapping("/pipeline")
    public Map<ClaimStatus, List<ClaimResponse>> pipeline() {
        return claims.pipeline();
    }

    @GetMapping
    public List<ClaimResponse> list(@RequestParam(required = false) ClaimStatus status,
                                    @RequestParam(required = false) UUID patientId) {
        return claims.list(status, patientId);
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

    /** Mueve una tarjeta entre columnas del Kanban y registra el cambio en el historial. */
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
