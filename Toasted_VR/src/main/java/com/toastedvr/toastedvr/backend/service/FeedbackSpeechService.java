package com.toastedvr.toastedvr.backend.service;

import java.util.Objects;

import com.toastedvr.toastedvr.backend.client.AiFeedbackClient;
import com.toastedvr.toastedvr.backend.client.AiSpeechClient;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.config.AiServiceProperties;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import org.springframework.stereotype.Service;

// Lee en voz alta la nota final y la retroalimentación de una sesión del jugador. El texto lo arma el
// backend desde la sesión guardada: Unity no manda texto, así nadie gasta la cuota de Azure con lo que quiera.
@Service
public class FeedbackSpeechService {

    private static final String SENTENCE_ENDINGS = ".!?";

    private final RoastingSessionService roastingSessionService;
    private final AiFeedbackClient aiFeedbackClient;
    private final AiSpeechClient aiSpeechClient;
    private final MessageResolver messages;
    private final int maxChars;

    public FeedbackSpeechService(
        RoastingSessionService roastingSessionService,
        AiFeedbackClient aiFeedbackClient,
        AiSpeechClient aiSpeechClient,
        MessageResolver messages,
        AiServiceProperties properties
    ) {
        this.roastingSessionService = roastingSessionService;
        this.aiFeedbackClient = aiFeedbackClient;
        this.aiSpeechClient = aiSpeechClient;
        this.messages = messages;
        this.maxChars = properties.getSpeechMaxChars();
    }

    public AiVoiceReply speak(Long userId, Long sessionId) {
        return aiSpeechClient.speak(spokenText(userId, sessionId));
    }

    String spokenText(Long userId, Long sessionId) {
        SessionResultResponse detail = roastingSessionService.getSessionDetail(userId, sessionId);
        RoastingSession session = roastingSessionService.getOwnedSession(userId, sessionId);
        // Igual que GET /feedback: el nivel con el que se jugó, o el actual en las sesiones viejas.
        KnowledgeLevel level = Objects.requireNonNullElse(session.getKnowledgeLevel(), session.getUser().getKnowledgeLevel());
        // El servicio IA guarda la retroalimentación en caché: si Unity ya la pidió, aquí no se genera otra vez.
        String feedback = aiFeedbackClient.generateFeedback(session, level);

        String score = messages.get(
            "roasting.feedback.speech.score",
            detail.finalScore(),
            messages.get("roasting.grade." + detail.grade())
        );
        String text = feedback == null || feedback.isBlank() ? score : score + " " + feedback.strip();
        return clip(text);
    }

    // Corta en el último final de frase que quepa, para que la voz no termine a mitad de palabra.
    private String clip(String text) {
        if (text.length() <= maxChars) {
            return text;
        }
        String head = text.substring(0, maxChars);
        int sentenceEnd = -1;
        for (char ending : SENTENCE_ENDINGS.toCharArray()) {
            sentenceEnd = Math.max(sentenceEnd, head.lastIndexOf(ending));
        }
        if (sentenceEnd > 0) {
            return head.substring(0, sentenceEnd + 1);
        }
        int space = head.lastIndexOf(' ');
        return (space > 0 ? head.substring(0, space) : head).strip();
    }
}
