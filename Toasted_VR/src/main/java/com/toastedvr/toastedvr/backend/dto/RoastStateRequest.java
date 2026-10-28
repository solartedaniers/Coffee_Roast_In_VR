package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotNull;

// Estado del tueste en el momento de la pregunta por voz, tal como lo ve Unity.
// El calor y la velocidad de subida son opcionales.
public record RoastStateRequest(
    @NotNull Double elapsedSeconds,
    @NotNull Double beanTemperature,
    @NotNull Boolean firstCrackReached,
    Double targetTemperature,
    Double chargeTemperature,
    Double firstCrackElapsedSeconds,
    Double heatLevelPercent,
    Double temperatureRisePerMinute
) {
}
