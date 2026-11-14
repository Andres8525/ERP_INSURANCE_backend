package com.erp.insurance.patients;

import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PatientDtos {
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    public static final String PHONE_PATTERN = "^[0-9()+\\-.\\s]{7,32}$";
    private static final String EMAIL_MESSAGE = "must be a valid email address with a domain such as name@mail.com";
    private static final String PHONE_MESSAGE = "must contain only digits, spaces, parentheses, dots or dashes";

    private PatientDtos() {}

    public record CreatePatientRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE) @Size(max = 320) String email,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) @Size(max = 32) String phone,
            @Size(max = 120) String city,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String dateOfBirth,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal householdIncome,
            AcaEligibilityStatus acaEligibilityStatus,
            AccountStatus accountStatus) {}

    public record UpdatePatientRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE) @Size(max = 320) String email,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) @Size(max = 32) String phone,
            @Size(max = 120) String city,
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String dateOfBirth,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal householdIncome,
            AcaEligibilityStatus acaEligibilityStatus,
            AccountStatus accountStatus) {}

    public record PatientResponse(UUID id, String crmId, String firstName, String lastName, String email, String phone, String city,
                                 String dateOfBirth, BigDecimal householdIncome,
                                 AcaEligibilityStatus acaEligibilityStatus, AccountStatus accountStatus,
                                 Instant createdAt) {
        static PatientResponse from(PatientEntity patient) {
            return new PatientResponse(patient.getId(), patient.getCrmId(), patient.getFirstName(), patient.getLastName(),
                    patient.getEmail(), patient.getPhone(), patient.getCity(), patient.getDateOfBirth(), patient.getHouseholdIncome() == null ? null : new BigDecimal(patient.getHouseholdIncome()),
                    patient.getAcaEligibilityStatus(), patient.getAccountStatus(), patient.getCreatedAt());
        }
    }
}
