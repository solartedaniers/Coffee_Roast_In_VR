package com.toastedvr.toastedvr.backend.service;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VerificationCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();
    private final int codeLength;

    public VerificationCodeGenerator(@Value("${app.verification.code-length:6}") int codeLength) {
        this.codeLength = codeLength;
    }

    public String generate() {
        int upperBound = (int) Math.pow(10, codeLength);
        int lowerBound = upperBound / 10;
        int randomNumber = secureRandom.nextInt(lowerBound, upperBound);
        return String.format("%0" + codeLength + "d", randomNumber);
    }
}
