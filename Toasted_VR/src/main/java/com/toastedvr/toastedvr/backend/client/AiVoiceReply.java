package com.toastedvr.toastedvr.backend.client;

// Respuesta del servicio IA tal cual: el estado y el JSON se le pasan a Unity sin cambios.
public record AiVoiceReply(int status, byte[] body) {
}
