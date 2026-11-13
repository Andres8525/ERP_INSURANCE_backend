package com.erp.insurance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Valida y publica la clave de cifrado al iniciar el contexto (falla rápido si es inválida). */
@Component
public class EncryptionKeyInitializer {
    public EncryptionKeyInitializer(@Value("${app.encryption.key}") String configuredKey) {
        EncryptionKeys.configure(configuredKey);
    }
}
