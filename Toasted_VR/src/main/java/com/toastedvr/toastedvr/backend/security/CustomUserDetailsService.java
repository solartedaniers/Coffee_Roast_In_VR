package com.toastedvr.toastedvr.backend.security;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
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
    private final MessageResolver messages;

    public CustomUserDetailsService(UserRepository userRepository, MessageResolver messages) {
        this.userRepository = userRepository;
        this.messages = messages;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
            .or(() -> userRepository.findByEmailIgnoreCase(normalizeEmail(username)))
            .orElseThrow(() -> new UsernameNotFoundException(messages.get("auth.userDetails.notFoundByCredentials")));

        return new UserPrincipal(user);
    }

    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(Objects.requireNonNull(userId, "userId must not be null"))
            .orElseThrow(() -> new UsernameNotFoundException(messages.get("auth.userDetails.notFoundById")));

        return new UserPrincipal(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
