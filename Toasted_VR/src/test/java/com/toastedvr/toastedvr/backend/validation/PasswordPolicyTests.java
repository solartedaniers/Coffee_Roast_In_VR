package com.toastedvr.toastedvr.backend.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTests {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy(8);

    @Test
    void shouldAcceptPasswordThatMeetsEveryRule() {
        assertThat(passwordPolicy.isSatisfiedBy("Password1")).isTrue();
    }

    @Test
    void shouldRejectPasswordShorterThanMinimum() {
        assertThat(passwordPolicy.isSatisfiedBy("Pass1ab")).isFalse();
    }

    @Test
    void shouldRejectPasswordWithoutUppercase() {
        assertThat(passwordPolicy.isSatisfiedBy("password1")).isFalse();
    }

    @Test
    void shouldRejectPasswordWithoutLowercase() {
        assertThat(passwordPolicy.isSatisfiedBy("PASSWORD1")).isFalse();
    }

    @Test
    void shouldRejectPasswordWithoutDigit() {
        assertThat(passwordPolicy.isSatisfiedBy("Passwordd")).isFalse();
    }

    @Test
    void shouldRejectNullPassword() {
        assertThat(passwordPolicy.isSatisfiedBy(null)).isFalse();
    }

    @Test
    void shouldUseConfiguredMinimumLength() {
        PasswordPolicy strictPolicy = new PasswordPolicy(12);

        assertThat(strictPolicy.isSatisfiedBy("Password1")).isFalse();
        assertThat(strictPolicy.isSatisfiedBy("Password1234")).isTrue();
    }
}
