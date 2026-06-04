package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveSessionRequest(
    @NotNull Double targetTemperature,
    @NotNull @Positive Integer totalDurationSeconds,
    @NotNull Double finalTemperature,
    @NotNull Double peakTemperature,
    @NotNull RoastingResult result,
    @NotNull @Min(0) @Max(100) Integer qualityScore,
    @NotNull Boolean firstCrackReached,
    @PositiveOrZero Integer developmentTimeSeconds
) {
}
