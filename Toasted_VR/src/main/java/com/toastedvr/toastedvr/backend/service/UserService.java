package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AuthenticatedUserResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthenticatedUserResponse updateKnowledgeLevel(Long userId, KnowledgeLevel knowledgeLevel) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        user.updateKnowledgeLevel(knowledgeLevel);
        userRepository.save(user);

        return new AuthenticatedUserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.isEmailVerified(),
            user.isEnabled(),
            user.getRole(),
            user.getLastLoginAt(),
            user.getKnowledgeLevel()
        );
    }
}
