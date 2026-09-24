package com.toastedvr.toastedvr.backend.exception;

public class InvalidVerificationCodeException extends ApiException {

    public InvalidVerificationCodeException(String message) {
        super(ErrorCode.INVALID_CODE, message);
    }
}
