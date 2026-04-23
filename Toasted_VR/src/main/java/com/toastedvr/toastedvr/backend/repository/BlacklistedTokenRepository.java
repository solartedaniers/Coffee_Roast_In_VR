package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.BlacklistedToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteByExpiresAtBefore(Instant instant);
}
