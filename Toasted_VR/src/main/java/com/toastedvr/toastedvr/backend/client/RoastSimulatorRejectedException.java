package com.toastedvr.toastedvr.backend.client;

// El simulador respondió 4xx con su código de error (por ejemplo SIMULATION_NOT_FOUND).
public class RoastSimulatorRejectedException extends RuntimeException {

    private final int status;
    private final String code;

    public RoastSimulatorRejectedException(int status, String code) {
        super("El simulador rechazó el pedido: " + status + " " + code);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
