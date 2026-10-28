package com.toastedvr.toastedvr.backend.client;

// Respuesta del servicio IA al pedir el paquete de alertas: 200 con el JSON o 304 sin cuerpo.
public record AiAlertPackageReply(int status, String etag, byte[] body) {
}
