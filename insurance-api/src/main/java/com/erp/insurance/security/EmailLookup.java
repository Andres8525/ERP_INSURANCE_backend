package com.erp.insurance.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EmailLookup {
    private EmailLookup() {}

    public static String hash(String email) {
        try {
            byte[] key = EncryptionKeys.current();
            byte[] label = "email-lookup-v1".getBytes(StandardCharsets.UTF_8);

            byte[] lookupKey = MessageDigest.getInstance("SHA-256").digest(
                    ByteBuffer.allocate(key.length + 19).put(key).put(label).array());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lookupKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not derive patient email lookup hash", exception);
        }
    }
}
