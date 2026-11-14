package com.erp.insurance.catalogs;

import com.erp.insurance.catalogs.CatalogDtos.CatalogInfo;
import com.erp.insurance.catalogs.CatalogDtos.Icd10Entry;
import com.erp.insurance.catalogs.CatalogDtos.Medication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@Tag(name = "Catálogos médicos", description = "CIE-10 y medicamentos; públicos y cacheables (Service Worker Cache-First)")
@RequestMapping("/api/catalogs")
public class CatalogController {
    static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic().staleWhileRevalidate(Duration.ofDays(7));

    private final CatalogService catalogs;

    public CatalogController(CatalogService catalogs) {
        this.catalogs = catalogs;
    }

    @Operation(summary = "Versión de los catálogos", description = "Cambia cuando cambia el contenido; el Service Worker la usa para invalidar su caché.")
    @GetMapping
    public ResponseEntity<CatalogInfo> info() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(catalogs.info());
    }

    @Operation(summary = "Catálogo CIE-10", description = "q busca por prefijo de código o texto de la descripción; chapter filtra por capítulo (I-XXII).")
    @GetMapping("/icd10")
    public ResponseEntity<List<Icd10Entry>> icd10(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String chapter) {
        return ResponseEntity.ok().cacheControl(CACHE).body(catalogs.icd10(q, chapter));
    }

    @Operation(summary = "Catálogo de medicamentos", description = "q busca por nombre, código ATC o código interno; controlled filtra medicamentos de control especial.")
    @GetMapping("/medications")
    public ResponseEntity<List<Medication>> medications(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) Boolean controlled) {
        return ResponseEntity.ok().cacheControl(CACHE).body(catalogs.medications(q, controlled));
    }
}
