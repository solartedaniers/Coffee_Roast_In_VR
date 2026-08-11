package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AuthenticatedUserResponse;
import com.toastedvr.toastedvr.backend.dto.UpdateProfileRequest;
import com.toastedvr.toastedvr.backend.exception.AuthenticationFailedException;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedUserResponse updateKnowledgeLevel(Long userId, KnowledgeLevel knowledgeLevel) {
        User user = findUser(userId);

        user.updateKnowledgeLevel(knowledgeLevel);
        userRepository.save(user);

        return AuthenticatedUserResponse.from(user);
    }

    @Transactional
    public AuthenticatedUserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        String normalizedUsername = request.username().trim();

        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, user.getId())) {
            throw new ConflictException("El nombre de usuario ya esta en uso.");
        }

        updatePasswordIfRequested(user, request.currentPassword(), request.newPassword());
        user.updateProfile(
            request.name().trim(),
            normalizedUsername,
            normalizeProfileImage(request.profileImageUrl())
        );

        if (request.knowledgeLevel() != null) {
            user.updateKnowledgeLevel(request.knowledgeLevel());
        }

        userRepository.save(user);
        return AuthenticatedUserResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
    }

    private void updatePasswordIfRequested(User user, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return;
        }

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new AuthenticationFailedException("Debes ingresar tu contraseña actual.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthenticationFailedException("La contraseña actual no es correcta.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
    }

    private String normalizeProfileImage(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }

        return profileImageUrl.trim();
    }
}
