package com.toastedvr.toastedvr.backend.exception;

import java.util.Map;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    protected ApiException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    protected ApiException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
