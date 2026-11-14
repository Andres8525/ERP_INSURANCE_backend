package com.erp.anomaly.domain;

import java.time.LocalDate;

public record PatientRecord(String id, String firstName, String lastName, String email, String phone,
                            LocalDate dateOfBirth) {
    public String fullName() {
        return (blankIfNull(firstName) + " " + blankIfNull(lastName)).trim();
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
