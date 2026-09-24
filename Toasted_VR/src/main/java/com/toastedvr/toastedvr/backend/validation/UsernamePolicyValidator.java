package com.toastedvr.toastedvr.backend.validation;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UsernamePolicyValidator implements ConstraintValidator<ValidUsername, String> {

    private final UsernamePolicy usernamePolicy;
    private final MessageResolver messages;

    public UsernamePolicyValidator(UsernamePolicy usernamePolicy, MessageResolver messages) {
        this.usernamePolicy = usernamePolicy;
        this.messages = messages;
    }

    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        if (username == null || username.isEmpty() || usernamePolicy.isSatisfiedBy(username.trim())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                messages.get("validation.username.length", usernamePolicy.getMinLength(), usernamePolicy.getMaxLength())
            )
            .addConstraintViolation();
        return false;
    }
}
