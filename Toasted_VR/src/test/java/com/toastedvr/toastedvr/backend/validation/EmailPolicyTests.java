package com.toastedvr.toastedvr.backend.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPolicyTests {

    private final EmailPolicy emailPolicy = new EmailPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"a@gmail.com", "user.name+tag@mail.udenar.edu.co", "ANA@Example.IO"})
    void shouldAcceptEmailsWithDomainAndExtension(String email) {
        assertThat(emailPolicy.isSatisfiedBy(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@gmailcom", "a@gmail.c", "a@gmail.", "a@.com", "a@gmail..com", "agmail.com", "a@gmail.c0m", "a b@gmail.com"})
    void shouldRejectEmailsWithoutValidDomain(String email) {
        assertThat(emailPolicy.isSatisfiedBy(email)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void shouldRejectNull() {
        assertThat(emailPolicy.isSatisfiedBy(null)).isFalse();
    }
}
