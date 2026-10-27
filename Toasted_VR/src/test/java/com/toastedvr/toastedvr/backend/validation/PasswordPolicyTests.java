package com.toastedvr.toastedvr.backend.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTests {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy(8, 72);

    @Test
    void shouldAcceptPasswordThatMeetsEveryRule() {
        assertThat(passwordPolicy.isSatisfiedBy("Password1")).isTrue();
    }

    @Test
    void shouldRejectPasswordShorterThanMinimum() {
        assertThat(passwordPolicy.isSatisfiedBy("Pass1ab")).isFalse();
    }

    @Test
    void shouldAcceptPasswordAtMaximumLength() {
        assertThat(passwordPolicy.isSatisfiedBy("Aa1" + "b".repeat(69))).isTrue();
    }

    @Test
    void shouldRejectPasswordLongerThanMaximum() {
        assertThat(passwordPolicy.isSatisfiedBy("Aa1" + "b".repeat(70))).isFalse();
    }

    @Test
    void shouldMeasureMaximumInUtf8BytesForBcrypt() {
        // 69 caracteres, pero "ñ" ocupa 2 bytes: 3 + 66 * 2 = 135 bytes.
        assertThat(passwordPolicy.isSatisfiedBy("Aa1" + "ñ".repeat(66))).isFalse();
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
    void shouldUseConfiguredLimits() {
        PasswordPolicy strictPolicy = new PasswordPolicy(12, 16);

        assertThat(strictPolicy.isSatisfiedBy("Password1")).isFalse();
        assertThat(strictPolicy.isSatisfiedBy("Password1234")).isTrue();
        assertThat(strictPolicy.isSatisfiedBy("Password12345678")).isTrue();
        assertThat(strictPolicy.isSatisfiedBy("Password123456789")).isFalse();
    }
}
