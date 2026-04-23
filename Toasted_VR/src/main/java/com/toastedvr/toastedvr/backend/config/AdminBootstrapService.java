package com.toastedvr.toastedvr.backend.config;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapService.class);

    @Bean
    public ApplicationRunner adminBootstrapRunner(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap-admin.enabled:false}") boolean enabled,
        @Value("${app.bootstrap-admin.name:}") String name,
        @Value("${app.bootstrap-admin.email:}") String email,
        @Value("${app.bootstrap-admin.username:}") String username,
        @Value("${app.bootstrap-admin.password:}") String password
    ) {
        return args -> {
            if (!enabled) {
                return;
            }

            if (name.isBlank() || email.isBlank() || username.isBlank() || password.isBlank()) {
                LOGGER.warn("Bootstrap admin enabled, but required values are missing.");
                return;
            }

            if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(username)) {
                LOGGER.info("Bootstrap admin was skipped because the email or username already exists.");
                return;
            }

            User adminUser = new User(
                name.trim(),
                email.trim().toLowerCase(),
                username.trim(),
                passwordEncoder.encode(password)
            );
            adminUser.markEmailAsVerified();
            adminUser.assignRole(Role.ADMIN);
            adminUser.activate();

            userRepository.save(adminUser);
            LOGGER.info("Bootstrap admin created successfully with username={}", username);
        };
    }
}
