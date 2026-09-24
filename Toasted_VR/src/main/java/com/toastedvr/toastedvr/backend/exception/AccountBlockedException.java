package com.toastedvr.toastedvr.backend.exception;

public class AccountBlockedException extends ApiException {

    public AccountBlockedException(String message) {
        super(ErrorCode.ACCOUNT_BLOCKED, message);
    }
}
