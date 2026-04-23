package com.toastedvr.toastedvr.backend.security;

import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
            .or(() -> userRepository.findByEmailIgnoreCase(normalizeEmail(username)))
            .orElseThrow(() -> new UsernameNotFoundException("No existe una cuenta con esas credenciales."));

        return new UserPrincipal(user);
    }

    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(Objects.requireNonNull(userId, "userId must not be null"))
            .orElseThrow(() -> new UsernameNotFoundException("No existe una cuenta con ese identificador."));

        return new UserPrincipal(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
