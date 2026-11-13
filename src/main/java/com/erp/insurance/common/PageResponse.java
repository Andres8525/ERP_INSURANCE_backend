package com.erp.insurance.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página estable para el frontend (evita serializar PageImpl de Spring, cuyo JSON no es un contrato fijo).
 * Reflejada en api.models.ts como PageResponse&lt;T&gt;. Las páginas empiezan en 0.
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size,
                              boolean first, boolean last) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize(), page.isFirst(), page.isLast());
    }
}
