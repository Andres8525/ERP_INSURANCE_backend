package com.erp.insurance.catalogs;

import com.erp.insurance.catalogs.CatalogDtos.CatalogInfo;
import com.erp.insurance.catalogs.CatalogDtos.Icd10Entry;
import com.erp.insurance.catalogs.CatalogDtos.Icd10Source;
import com.erp.insurance.catalogs.CatalogDtos.Medication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CatalogService {
    public static final Map<String, String> CHAPTERS = Map.ofEntries(
            Map.entry("I", "Enfermedades infecciosas y parasitarias"),
            Map.entry("II", "Tumores (neoplasias)"),
            Map.entry("III", "Enfermedades de la sangre y del sistema inmunitario"),
            Map.entry("IV", "Enfermedades endocrinas, nutricionales y metabólicas"),
            Map.entry("V", "Trastornos mentales y del comportamiento"),
            Map.entry("VI", "Enfermedades del sistema nervioso"),
            Map.entry("VII", "Enfermedades del ojo y sus anexos"),
            Map.entry("VIII", "Enfermedades del oído y de la apófisis mastoides"),
            Map.entry("IX", "Enfermedades del sistema circulatorio"),
            Map.entry("X", "Enfermedades del sistema respiratorio"),
            Map.entry("XI", "Enfermedades del sistema digestivo"),
            Map.entry("XII", "Enfermedades de la piel y del tejido subcutáneo"),
            Map.entry("XIII", "Enfermedades del sistema osteomuscular"),
            Map.entry("XIV", "Enfermedades del sistema genitourinario"),
            Map.entry("XV", "Embarazo, parto y puerperio"),
            Map.entry("XVI", "Afecciones originadas en el período perinatal"),
            Map.entry("XVII", "Malformaciones congénitas"),
            Map.entry("XVIII", "Síntomas y hallazgos anormales"),
            Map.entry("XIX", "Traumatismos y envenenamientos"),
            Map.entry("XX", "Causas externas de morbilidad"),
            Map.entry("XXI", "Factores que influyen en el estado de salud"),
            Map.entry("XXII", "Códigos para propósitos especiales"));

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private final List<Icd10Entry> icd10;
    private final List<Medication> medications;
    private final String version;

    public CatalogService(ObjectMapper json) {
        byte[] icd10Bytes = read("catalogs/icd10.json");
        byte[] medicationBytes = read("catalogs/medications.json");
        try {
            List<Icd10Source> sources = json.readValue(icd10Bytes, new TypeReference<>() {});
            this.icd10 = sources.stream()
                    .map(source -> new Icd10Entry(source.code(), source.description(), source.chapter(),
                            CHAPTERS.getOrDefault(source.chapter(), "Capítulo " + source.chapter())))
                    .toList();
            this.medications = List.copyOf(json.readValue(medicationBytes, new TypeReference<List<Medication>>() {}));
        } catch (IOException exception) {
            throw new UncheckedIOException("Catálogos médicos inválidos", exception);
        }
        this.version = digest(icd10Bytes, medicationBytes);
    }

    public CatalogInfo info() {
        return new CatalogInfo(version, icd10.size(), medications.size());
    }

    public String version() {
        return version;
    }

    public List<Icd10Entry> icd10(String query, String chapter) {
        String needle = normalize(query);
        return icd10.stream()
                .filter(entry -> chapter == null || chapter.isBlank() || entry.chapter().equalsIgnoreCase(chapter.trim()))
                .filter(entry -> needle.isEmpty()
                        || normalize(entry.code()).startsWith(needle)
                        || normalize(entry.description()).contains(needle))
                .toList();
    }

    public List<Medication> medications(String query, Boolean controlled) {
        String needle = normalize(query);
        return medications.stream()
                .filter(medication -> controlled == null || medication.controlled() == controlled)
                .filter(medication -> needle.isEmpty()
                        || normalize(medication.name()).contains(needle)
                        || normalize(medication.atc()).startsWith(needle)
                        || normalize(medication.code()).equals(needle))
                .toList();
    }

    public List<Icd10Entry> allIcd10() {
        return icd10;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static byte[] read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer " + path, exception);
        }
    }

    private static String digest(byte[]... parts) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) sha.update(part);
            return HexFormat.of().formatHex(sha.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
