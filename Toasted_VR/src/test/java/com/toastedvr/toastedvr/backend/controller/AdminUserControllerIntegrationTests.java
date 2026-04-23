package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User admin = createUser("Alexander", "alexander@toastedvr.test", "Alexander", Role.ADMIN, true);
        createUser("Danier", "danier@toastedvr.test", "Danier", Role.PLAYER, true);
        createUser("Omaira", "omaira@toastedvr.test", "Omaira", Role.PLAYER, false);

        adminToken = jwtService.generateToken(new UserPrincipal(admin));
    }

    @Test
    void shouldListUsersWithoutOptionalFilters() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    private User createUser(String name, String email, String username, Role role, boolean enabled) {
        User user = new User(name, email, username, passwordEncoder.encode("Password123!"));
        user.markEmailAsVerified();
        user.assignRole(role);

        if (enabled) {
            user.activate();
        } else {
            user.block();
        }

        return userRepository.save(user);
    }
}
