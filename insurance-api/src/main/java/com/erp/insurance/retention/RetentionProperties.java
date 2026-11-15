package com.erp.insurance.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.retention")
public record RetentionProperties(@DefaultValue("12") int archiveAfterMonths,
                                  @DefaultValue("7") int retentionYears,
                                  @DefaultValue("0.023") double hotUsdPerGbMonth,
                                  @DefaultValue("0.004") double archiveUsdPerGbMonth,
                                  @DefaultValue("180") double patientFileMb,
                                  @DefaultValue("45") double policyDocumentMb,
                                  @DefaultValue("60") double claimDocumentMb,
                                  @DefaultValue("15") double claimServiceMb) {
}
