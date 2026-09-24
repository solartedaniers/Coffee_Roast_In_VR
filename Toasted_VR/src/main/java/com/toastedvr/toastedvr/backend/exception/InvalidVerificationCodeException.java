package com.toastedvr.toastedvr.backend.exception;

import java.util.Map;

public class InvalidVerificationCodeException extends ApiException {

    public InvalidVerificationCodeException(String message) {
        super(ErrorCode.INVALID_CODE, message);
    }

    public InvalidVerificationCodeException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details, null);
    }
}
