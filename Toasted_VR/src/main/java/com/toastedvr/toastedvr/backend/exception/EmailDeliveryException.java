package com.toastedvr.toastedvr.backend.exception;

public class EmailDeliveryException extends ApiException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(ErrorCode.EMAIL_DELIVERY_FAILED, message, null, cause);
    }
}
