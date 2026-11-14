package com.erp.anomaly.rules;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextNormalizer {
    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]");

    private TextNormalizer() {}

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String identityKey(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String plain = DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        return NON_LETTERS.matcher(plain).replaceAll("");
    }

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
