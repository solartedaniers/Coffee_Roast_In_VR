package com.toastedvr.toastedvr.backend.exception;

public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }

    /** Error que pertenece a un campo concreto del formulario. */
    public AuthenticationFailedException(String message, String field) {
        super(ErrorCode.AUTHENTICATION_FAILED, message, FieldErrors.of(field, message), null);
    }
}
