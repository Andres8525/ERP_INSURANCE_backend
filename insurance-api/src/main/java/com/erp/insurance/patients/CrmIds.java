package com.erp.insurance.patients;

import java.security.SecureRandom;

public final class CrmIds {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String PREFIX = "CRM-";
    public static final int LENGTH = 8;

    private CrmIds() {}

    public static String next() {
        StringBuilder value = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) value.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        return value.toString();
    }

    public static boolean isValid(String value) {
        return value != null && value.matches("^CRM-[0-9A-Z]{8}$");
    }
}
