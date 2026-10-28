package com.toastedvr.toastedvr.backend.dto;

// Mismo formato de error que responde el servicio IA, para que Unity lea siempre code y message.
public record VoiceAssistantErrorResponse(String code, String message) {
}
