package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AuthenticatedUserResponse;
import com.toastedvr.toastedvr.backend.dto.UpdateProfileRequest;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.InvalidRequestException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.validation.UsernamePolicy;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageResolver messages;
    private final UsernamePolicy usernamePolicy;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        MessageResolver messages,
        UsernamePolicy usernamePolicy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.messages = messages;
        this.usernamePolicy = usernamePolicy;
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

        // Solo se exige la longitud si el username cambia, para no bloquear a
        // usuarios creados antes de que existiera el límite.
        boolean usernameChanged = !normalizedUsername.equals(user.getUsername());
        if (usernameChanged && !usernamePolicy.isSatisfiedBy(normalizedUsername)) {
            throw new InvalidRequestException(
                messages.get("validation.username.length", usernamePolicy.getMinLength(), usernamePolicy.getMaxLength()),
                "username"
            );
        }

        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, user.getId())) {
            throw new ConflictException(messages.get("user.profile.usernameTaken"), "username");
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
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("user.notFound")));
    }

    private void updatePasswordIfRequested(User user, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return;
        }

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new InvalidRequestException(messages.get("user.profile.currentPasswordRequired"), "currentPassword");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidRequestException(messages.get("user.profile.currentPasswordIncorrect"), "currentPassword");
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
