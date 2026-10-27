package com.toastedvr.toastedvr.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import com.toastedvr.toastedvr.backend.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Permiso corto para cambiar la contraseña después de verificar el código de
// recuperación. Se firma con una clave derivada solo para esto, así que nunca
// sirve como token de sesión. Lleva una huella de la contraseña actual: en
// cuanto la contraseña cambia, el token deja de valer (un solo uso).
@Service
public class PasswordResetTokenService {

    private static final String PASSWORD_FINGERPRINT_CLAIM = "pwd";
    private static final byte[] KEY_PURPOSE = ":password-reset".getBytes(StandardCharsets.UTF_8);

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final TokenHasher tokenHasher;

    public PasswordResetTokenService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.password-reset.token-expiration-minutes:10}") long expirationMinutes,
        TokenHasher tokenHasher
    ) {
        this.signingKey = Keys.hmacShaKeyFor(deriveKey(Decoders.BASE64.decode(secret)));
        this.expirationSeconds = expirationMinutes * 60;
        this.tokenHasher = tokenHasher;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim(PASSWORD_FINGERPRINT_CLAIM, fingerprint(user))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds)))
            .signWith(signingKey)
            .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /** Id del usuario si el token es auténtico y no ha vencido. */
    public Optional<Long> readUserId(String token) {
        return parse(token).map(claims -> Long.valueOf(claims.getSubject()));
    }

    /** true si el token es de este usuario y su contraseña no cambió desde que se emitió. */
    public boolean isValidFor(String token, User user) {
        return parse(token)
            .filter(claims -> String.valueOf(user.getId()).equals(claims.getSubject()))
            .map(claims -> fingerprint(user).equals(claims.get(PASSWORD_FINGERPRINT_CLAIM, String.class)))
            .orElse(false);
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String fingerprint(User user) {
        return tokenHasher.hash(user.getPassword());
    }

    private static byte[] deriveKey(byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            return digest.digest(KEY_PURPOSE);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to derive password reset key.", exception);
        }
    }
}
