package com.erp.insurance.integration.anomaly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.anomaly-engine")
public record AnomalyEngineProperties(String address,
                                      @DefaultValue("false") boolean tls,
                                      @DefaultValue("8s") Duration deadline,
                                      @DefaultValue("true") boolean fallbackInProcess) {
    public boolean hasRemote() {
        return address != null && !address.isBlank();
    }
}
