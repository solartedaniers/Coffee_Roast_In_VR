package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.BlacklistedToken;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.security.TokenHasher;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final TokenHasher tokenHasher;

    public TokenBlacklistService(BlacklistedTokenRepository blacklistedTokenRepository, TokenHasher tokenHasher) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public void blacklistToken(String token, Instant expiresAt) {
        cleanupExpiredTokens();

        String tokenHash = tokenHasher.hash(token);
        if (blacklistedTokenRepository.existsByTokenHashAndExpiresAtAfter(tokenHash, Instant.now())) {
            return;
        }

        blacklistedTokenRepository.save(new BlacklistedToken(tokenHash, expiresAt));
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        return blacklistedTokenRepository.existsByTokenHashAndExpiresAtAfter(tokenHasher.hash(token), Instant.now());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
