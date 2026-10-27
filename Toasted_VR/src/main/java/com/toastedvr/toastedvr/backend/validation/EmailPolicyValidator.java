package com.toastedvr.toastedvr.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EmailPolicyValidator implements ConstraintValidator<ValidEmail, String> {

    private final EmailPolicy emailPolicy;

    public EmailPolicyValidator(EmailPolicy emailPolicy) {
        this.emailPolicy = emailPolicy;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email == null || email.isEmpty() || emailPolicy.isSatisfiedBy(email);
    }
}
