package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toastedvr.toastedvr.backend.client.AiFeedbackClient;
import com.toastedvr.toastedvr.backend.client.AiSpeechClient;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.config.AiServiceProperties;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@SuppressWarnings("null")
class FeedbackSpeechServiceTests {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final String FEEDBACK = "Bajaste bien el gas en el secado. La próxima vez descarga un poco antes.";

    private final RoastingSessionService sessions = mock(RoastingSessionService.class);
    private final AiFeedbackClient feedbackClient = mock(AiFeedbackClient.class);
    private final AiSpeechClient speechClient = mock(AiSpeechClient.class);
    private final AiServiceProperties properties = new AiServiceProperties();
    private final User player = new User("Player", "player@toastedvr.test", "player", "hash");
    private RoastingSession session;

    @BeforeEach
    void setUp() {
        player.updateKnowledgeLevel(KnowledgeLevel.ADVANCED);
        session = sessionPlayedAt(KnowledgeLevel.BEGINNER);
        when(sessions.getSessionDetail(USER_ID, SESSION_ID)).thenReturn(detail(78, "GOOD"));
        when(sessions.getOwnedSession(USER_ID, SESSION_ID)).thenReturn(session);
    }

    @Test
    void readsTheFinalScoreWithItsGradeAndThenTheFeedback() {
        when(feedbackClient.generateFeedback(session, KnowledgeLevel.BEGINNER)).thenReturn("  " + FEEDBACK + "\n");

        assertThat(service().spokenText(USER_ID, SESSION_ID)).isEqualTo("Tu nota final es 78 %, Bueno. " + FEEDBACK);
    }

    @Test
    void readsOnlyTheScoreWhenTheFeedbackIsNotAvailable() {
        when(feedbackClient.generateFeedback(any(), any())).thenReturn(null);

        assertThat(service().spokenText(USER_ID, SESSION_ID)).isEqualTo("Tu nota final es 78 %, Bueno.");
    }

    @Test
    void readsOnlyTheScoreWhenTheFeedbackIsBlank() {
        when(feedbackClient.generateFeedback(any(), any())).thenReturn("   ");

        assertThat(service().spokenText(USER_ID, SESSION_ID)).isEqualTo("Tu nota final es 78 %, Bueno.");
    }

    @Test
    void namesEveryGradeInSpanish() {
        when(sessions.getSessionDetail(USER_ID, SESSION_ID)).thenReturn(detail(95, "EXCELLENT"));

        assertThat(service().spokenText(USER_ID, SESSION_ID)).isEqualTo("Tu nota final es 95 %, Excelente.");
    }

    @Test
    void usesTheCurrentLevelForOldSessionsWithoutOne() {
        RoastingSession oldSession = sessionPlayedAt(null);
        when(sessions.getOwnedSession(USER_ID, SESSION_ID)).thenReturn(oldSession);

        service().spokenText(USER_ID, SESSION_ID);

        verify(feedbackClient).generateFeedback(oldSession, KnowledgeLevel.ADVANCED);
    }

    @Test
    void cutsALongTextAtTheLastSentenceThatFits() {
        properties.setSpeechMaxChars(64);
        when(feedbackClient.generateFeedback(any(), any())).thenReturn(FEEDBACK);

        String text = service().spokenText(USER_ID, SESSION_ID);

        assertThat(text).isEqualTo("Tu nota final es 78 %, Bueno. Bajaste bien el gas en el secado.");
        assertThat(text.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void cutsAtAWordWhenNoSentenceEndFits() {
        properties.setSpeechMaxChars(20);

        String text = service().spokenText(USER_ID, SESSION_ID);

        assertThat(text).isEqualTo("Tu nota final es 78");
    }

    @Test
    void sendsTheTextToTheAiServiceAndPassesTheReplyThrough() {
        AiVoiceReply reply = new AiVoiceReply(200, new byte[] {1});
        when(feedbackClient.generateFeedback(any(), any())).thenReturn(FEEDBACK);
        when(speechClient.speak("Tu nota final es 78 %, Bueno. " + FEEDBACK)).thenReturn(reply);

        assertThat(service().speak(USER_ID, SESSION_ID)).isSameAs(reply);
    }

    @Test
    void doesNotSpeakASessionOfAnotherPlayer() {
        when(sessions.getSessionDetail(USER_ID, SESSION_ID)).thenThrow(new ResourceNotFoundException("no"));

        assertThatThrownBy(() -> service().speak(USER_ID, SESSION_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(speechClient, never()).speak(anyString());
    }

    private FeedbackSpeechService service() {
        return new FeedbackSpeechService(sessions, feedbackClient, speechClient, messages(), properties);
    }

    private RoastingSession sessionPlayedAt(KnowledgeLevel level) {
        return new RoastingSession(player, 190.0, 210.0, 600, 205.0, 206.0, RoastingResult.PERFECT, 78, true, 90, level);
    }

    private static SessionResultResponse detail(int finalScore, String grade) {
        return new SessionResultResponse(SESSION_ID, "PERFECT", finalScore, 190.0, 210.0, 600, 205.0, 206.0, true, 90,
            0.15, KnowledgeLevel.BEGINNER, null, finalScore, grade, null);
    }

    private static MessageResolver messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new MessageResolver(source);
    }
}
