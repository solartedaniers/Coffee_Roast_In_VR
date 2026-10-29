package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.client.AiAlertPackageClient;
import com.toastedvr.toastedvr.backend.client.AiAlertPackageReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Pide el paquete de alertas del nivel actual del jugador, tomado de la base:
// Unity nunca elige el nivel.
@Service
public class AlertPackageService {

    private final UserRepository userRepository;
    private final AiAlertPackageClient aiAlertPackageClient;
    private final MessageResolver messages;

    public AlertPackageService(
        UserRepository userRepository,
        AiAlertPackageClient aiAlertPackageClient,
        MessageResolver messages
    ) {
        this.userRepository = userRepository;
        this.aiAlertPackageClient = aiAlertPackageClient;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public AiAlertPackageReply fetch(Long userId, String ifNoneMatch) {
        User user = userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("auth.userDetails.notFoundById")));
        return aiAlertPackageClient.fetch(user.getKnowledgeLevel(), ifNoneMatch);
    }
}
