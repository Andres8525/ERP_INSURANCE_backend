package com.erp.insurance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyInitializer {
    public EncryptionKeyInitializer(@Value("${app.encryption.key}") String configuredKey) {
        EncryptionKeys.configure(configuredKey);
    }
}
