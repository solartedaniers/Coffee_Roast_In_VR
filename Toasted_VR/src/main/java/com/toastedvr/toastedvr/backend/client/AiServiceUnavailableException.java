package com.toastedvr.toastedvr.backend.client;

// El servicio IA no respondió (apagado, sin red o se pasó del tiempo de espera).
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
