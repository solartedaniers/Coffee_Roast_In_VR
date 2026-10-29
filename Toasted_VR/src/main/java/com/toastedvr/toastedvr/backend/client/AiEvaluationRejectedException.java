package com.toastedvr.toastedvr.backend.client;

// El servicio IA rechazó la traza (4xx): reintentar no cambiaría la respuesta.
public class AiEvaluationRejectedException extends RuntimeException {

    public AiEvaluationRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
