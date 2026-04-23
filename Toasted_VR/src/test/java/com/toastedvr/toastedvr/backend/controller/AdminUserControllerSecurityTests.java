package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String playerToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User admin = buildUser("Admin User", "admin@toastedvr.test", "adminUser", Role.ADMIN);
        User player = buildUser("Player User", "player@toastedvr.test", "playerUser", Role.PLAYER);
        User savedAdmin = requireUser(userRepository.save(admin));
        User savedPlayer = requireUser(userRepository.save(player));

        adminToken = jwtService.generateToken(new UserPrincipal(savedAdmin));
        playerToken = jwtService.generateToken(new UserPrincipal(savedPlayer));
    }

    @Test
    void shouldRejectAdminEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAdminEndpointForNonAdminRole() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + playerToken)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminEndpointForAdminRole() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk());
    }

    private @NonNull User buildUser(String name, String email, String username, Role role) {
        User user = new User(name, email, username, passwordEncoder.encode("Password123!"));
        user.markEmailAsVerified();
        user.assignRole(role);
        user.activate();
        return user;
    }

    private @NonNull User requireUser(@Nullable User user) {
        return Objects.requireNonNull(user, "Saved user must not be null.");
    }
}
