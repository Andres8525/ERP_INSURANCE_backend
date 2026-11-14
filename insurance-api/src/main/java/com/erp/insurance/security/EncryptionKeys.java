package com.erp.insurance.security;

import java.util.Base64;

/**
 * Clave AES-256 compartida por {@link SensitiveDataConverter} y {@link EmailLookup}.
 * Los conversores JPA los instancia Hibernate (no Spring), por eso la clave se publica aquí
 * una sola vez al arrancar desde {@code app.encryption.key} (ver {@link EncryptionKeyInitializer}).
 * Si no se configuró, se intenta la variable de entorno ENCRYPTION_KEY.
 */
public final class EncryptionKeys {
    private static volatile byte[] key;

    private EncryptionKeys() {}

    static void configure(String base64) {
        key = decode(base64);
    }

    static byte[] current() {
        byte[] configured = key;
        if (configured != null) return configured.clone();
        return decode(System.getenv("ENCRYPTION_KEY"));
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("ENCRYPTION_KEY is required to access encrypted data");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ENCRYPTION_KEY must be valid Base64", exception);
        }
        if (bytes.length != 32) throw new IllegalStateException("ENCRYPTION_KEY must decode to exactly 32 bytes");
        return bytes;
    }
}
