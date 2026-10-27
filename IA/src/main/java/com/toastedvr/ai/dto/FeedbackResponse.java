package com.toastedvr.ai.dto;

// feedback es null cuando Ollama no respondió; el backend lo convierte en "".
public record FeedbackResponse(String feedback) {
}
