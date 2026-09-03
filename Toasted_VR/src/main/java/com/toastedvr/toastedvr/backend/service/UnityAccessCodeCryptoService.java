package com.toastedvr.toastedvr.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UnityAccessCodeCryptoService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int ENCRYPTION_KEY_VERSION = 1;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] hmacPepper;
    private final SecretKeySpec encryptionKey;

    public UnityAccessCodeCryptoService(
        @Value("${app.unity-code.hmac-pepper}") String hmacPepper,
        @Value("${app.unity-code.encryption-key}") String encryptionKey
    ) {
        if (hmacPepper == null || hmacPepper.isBlank()) {
            throw new IllegalStateException("UNITY_CODE_HMAC_PEPPER must be configured.");
        }

        this.hmacPepper = hmacPepper.getBytes(StandardCharsets.UTF_8);
        this.encryptionKey = new SecretKeySpec(decodeEncryptionKey(encryptionKey), "AES");
    }

    public String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        String normalized = code.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() != CODE_LENGTH || !normalized.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}")) {
            return null;
        }

        return normalized;
    }

    public String lookupHash(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacPepper, "HmacSHA256"));
            return toHex(mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate the Unity access code hash.", exception);
        }
    }

    public EncryptedCode encrypt(String normalizedCode) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCode(
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(iv),
                ENCRYPTION_KEY_VERSION
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt the Unity access code.", exception);
        }
    }

    public String decrypt(String encryptedCode, String encryptionIv, int encryptionKeyVersion) {
        if (encryptionKeyVersion != ENCRYPTION_KEY_VERSION) {
            throw new IllegalStateException("Unsupported Unity access code encryption key version.");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey,
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(encryptionIv))
            );
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedCode)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt the Unity access code.", exception);
        }
    }

    public String formatCode(String normalizedCode) {
        return normalizedCode.substring(0, 4) + "-" + normalizedCode.substring(4);
    }

    private byte[] decodeEncryptionKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("UNITY_CODE_ENCRYPTION_KEY must be configured.");
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            if (decodedKey.length != 32) {
                throw new IllegalStateException("UNITY_CODE_ENCRYPTION_KEY must be a Base64-encoded 256-bit AES key.");
            }
            return decodedKey;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("UNITY_CODE_ENCRYPTION_KEY must be a valid Base64 value.", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    public record EncryptedCode(String encryptedCode, String encryptionIv, int encryptionKeyVersion) {
    }
}
