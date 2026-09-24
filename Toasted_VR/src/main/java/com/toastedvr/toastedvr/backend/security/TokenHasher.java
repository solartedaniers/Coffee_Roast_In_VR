package com.toastedvr.toastedvr.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

// Hash SHA-256 en hexadecimal para guardar tokens y códigos sin exponer su
// valor original en la base de datos.
@Component
public class TokenHasher {

    public String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash token.", exception);
        }
    }
}
