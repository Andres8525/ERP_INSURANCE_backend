package com.erp.insurance.catalogs;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record Icd10Source(String code, String description, String chapter) {}

    public record Icd10Entry(String code, String description, String chapter, String chapterName) {}

    public record Medication(String code, String name, String presentation, String atc, boolean controlled) {}

    public record CatalogInfo(String version, int icd10Codes, int medications) {}
}
