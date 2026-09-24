package com.toastedvr.toastedvr.backend.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    /** Error que pertenece a un campo concreto del formulario. */
    public ConflictException(String message, String field) {
        super(ErrorCode.CONFLICT, message, FieldErrors.of(field, message), null);
    }
}
