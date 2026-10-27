package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toastedvr.toastedvr.backend.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    ErrorCode code,
    Map<String, Object> details
) {
}
