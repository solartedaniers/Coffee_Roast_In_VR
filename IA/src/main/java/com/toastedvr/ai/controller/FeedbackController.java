package com.toastedvr.ai.controller;

import com.toastedvr.ai.dto.FeedbackResponse;
import com.toastedvr.ai.dto.RoastFeedbackRequest;
import com.toastedvr.ai.service.OllamaFeedbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final OllamaFeedbackService ollamaFeedbackService;

    public FeedbackController(OllamaFeedbackService ollamaFeedbackService) {
        this.ollamaFeedbackService = ollamaFeedbackService;
    }

    @PostMapping
    public FeedbackResponse generateFeedback(@RequestBody RoastFeedbackRequest request) {
        return new FeedbackResponse(ollamaFeedbackService.generateFeedback(request, request.getKnowledgeLevel()));
    }
}
