package com.toastedvr.toastedvr.backend.exception;

public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }
}
