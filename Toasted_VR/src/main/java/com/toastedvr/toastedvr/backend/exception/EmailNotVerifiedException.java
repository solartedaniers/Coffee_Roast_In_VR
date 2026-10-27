package com.toastedvr.toastedvr.backend.exception;

public class EmailNotVerifiedException extends ApiException {

    public EmailNotVerifiedException(String message) {
        super(ErrorCode.EMAIL_NOT_VERIFIED, message);
    }
}
