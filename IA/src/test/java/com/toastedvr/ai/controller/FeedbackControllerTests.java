package com.toastedvr.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toastedvr.ai.domain.KnowledgeLevel;
import com.toastedvr.ai.domain.RoastingResult;
import com.toastedvr.ai.service.OllamaFeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeedbackController.class)
class FeedbackControllerTests {

    private static final String SESSION_JSON = """
        {"result": "PERFECT", "qualityScore": 92, "chargeTemperature": 190.0, "targetTemperature": 200.0,
         "finalTemperature": 201.0, "totalDurationSeconds": 780, "firstCrackReached": true,
         "developmentTimeSeconds": 156, "knowledgeLevel": "ADVANCED"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaFeedbackService ollamaFeedbackService;

    @Test
    @SuppressWarnings("null")
    void generatesFeedbackWithTheLevelSentByTheBackend() throws Exception {
        when(ollamaFeedbackService.generateFeedback(any(), any())).thenReturn("Buen tueste.");

        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON).content(SESSION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").value("Buen tueste."));

        verify(ollamaFeedbackService).generateFeedback(
            argThat(request -> request.getResult() == RoastingResult.PERFECT && request.getQualityScore() == 92),
            eq(KnowledgeLevel.ADVANCED)
        );
    }

    @Test
    @SuppressWarnings("null")
    void answersNullFeedbackWhenOllamaIsNotAvailable() throws Exception {
        when(ollamaFeedbackService.generateFeedback(any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON).content(SESSION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").doesNotExist());
    }
}
