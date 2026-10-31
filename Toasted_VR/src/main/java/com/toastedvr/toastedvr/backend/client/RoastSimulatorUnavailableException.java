package com.toastedvr.toastedvr.backend.client;

// El simulador no respondió, falló (5xx) o devolvió algo que no se puede leer.
public class RoastSimulatorUnavailableException extends RuntimeException {

    public RoastSimulatorUnavailableException(String message) {
        super(message);
    }

    public RoastSimulatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
