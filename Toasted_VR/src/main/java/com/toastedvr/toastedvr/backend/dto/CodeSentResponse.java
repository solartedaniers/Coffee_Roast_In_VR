package com.toastedvr.toastedvr.backend.dto;

public record CodeSentResponse(
    String message,
    String email,
    OneTimeCodePolicyResponse codePolicy
) {
}
