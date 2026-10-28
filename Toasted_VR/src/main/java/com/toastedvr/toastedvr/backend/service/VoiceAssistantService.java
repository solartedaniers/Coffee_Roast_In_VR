package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.client.AiVoiceAssistantClient;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AiVoiceQuestionContext;
import com.toastedvr.toastedvr.backend.dto.VoiceQuestionRequest;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Agrega a la pregunta por voz el usuario y su nivel actual, tomados de la base,
// y la reenvía al servicio IA.
@Service
public class VoiceAssistantService {

    private final UserRepository userRepository;
    private final AiVoiceAssistantClient aiVoiceAssistantClient;
    private final MessageResolver messages;

    public VoiceAssistantService(
        UserRepository userRepository,
        AiVoiceAssistantClient aiVoiceAssistantClient,
        MessageResolver messages
    ) {
        this.userRepository = userRepository;
        this.aiVoiceAssistantClient = aiVoiceAssistantClient;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public AiVoiceReply ask(Long userId, VoiceQuestionRequest request, byte[] wav) {
        User user = userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("auth.userDetails.notFoundById")));
        return aiVoiceAssistantClient.ask(AiVoiceQuestionContext.from(user.getId(), user.getKnowledgeLevel(), request), wav);
    }
}
