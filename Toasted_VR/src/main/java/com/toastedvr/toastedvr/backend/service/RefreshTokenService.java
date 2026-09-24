package com.toastedvr.toastedvr.backend.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Ciclo de vida del refresh token: el cliente recibe el valor opaco y en la
// base de datos solo queda su hash. La vigencia cuenta desde el último uso,
// así un usuario activo nunca pierde la sesión.
@Service
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final TokenHasher tokenHasher;
    private final long expirationHours;

    public RefreshTokenService(
        UserRepository userRepository,
        TokenHasher tokenHasher,
        @Value("${app.jwt.refresh-expiration-hours:24}") long expirationHours
    ) {
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.expirationHours = expirationHours;
    }

    /** Emite un refresh token nuevo para el usuario y devuelve su valor en claro. */
    public String issue(User user) {
        String refreshToken = UUID.randomUUID().toString();
        user.updateRefreshTokenHash(tokenHasher.hash(refreshToken), LocalDateTime.now().plusHours(expirationHours));
        return refreshToken;
    }

    public Optional<User> findOwner(String refreshToken) {
        return userRepository.findByRefreshTokenHash(tokenHasher.hash(refreshToken));
    }

    public boolean isExpired(User user) {
        return user.getRefreshTokenExpiresAt() == null
            || user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now());
    }
}
