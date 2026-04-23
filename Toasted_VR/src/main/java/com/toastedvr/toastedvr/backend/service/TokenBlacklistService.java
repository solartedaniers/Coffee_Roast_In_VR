package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.BlacklistedToken;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public TokenBlacklistService(BlacklistedTokenRepository blacklistedTokenRepository) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    @Transactional
    public void blacklistToken(String token, Instant expiresAt) {
        cleanupExpiredTokens();

        String tokenHash = hashToken(token);
        if (blacklistedTokenRepository.existsByTokenHashAndExpiresAtAfter(tokenHash, Instant.now())) {
            return;
        }

        blacklistedTokenRepository.save(new BlacklistedToken(tokenHash, expiresAt));
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        return blacklistedTokenRepository.existsByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder();
            for (byte hashByte : digest) {
                builder.append(String.format("%02x", hashByte));
            }

            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash token.", exception);
        }
    }
}
