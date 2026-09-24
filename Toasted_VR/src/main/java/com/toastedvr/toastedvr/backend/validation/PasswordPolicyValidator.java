package com.toastedvr.toastedvr.backend.validation;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private final PasswordPolicy passwordPolicy;
    private final MessageResolver messages;

    public PasswordPolicyValidator(PasswordPolicy passwordPolicy, MessageResolver messages) {
        this.passwordPolicy = passwordPolicy;
        this.messages = messages;
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty() || passwordPolicy.isSatisfiedBy(password)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                messages.get("validation.password.policy", passwordPolicy.getMinLength())
            )
            .addConstraintViolation();
        return false;
    }
}
