package com.toastedvr.toastedvr.backend.validation;

import com.toastedvr.toastedvr.backend.config.EmailDomainProperties;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDomainPolicyTests {

    private final EmailDomainProperties properties = new EmailDomainProperties();
    private final EmailDomainLookup lookup = mock(EmailDomainLookup.class);
    private final EmailDomainPolicy policy = new EmailDomainPolicy(properties, lookup, messages());

    @ParameterizedTest
    @CsvSource({
        "gail.com, gmail.com",
        "gmial.com, gmail.com",
        "gmaill.com, gmail.com",
        "gmail.con, gmail.com",
        "hotmial.com, hotmail.com",
        "hotmail.co, hotmail.com",
        "outlok.com, outlook.com",
        "yahooo.com, yahoo.com",
        "icloud.cm, icloud.com"
    })
    void shouldSuggestTheProviderForATypo(String typo, String provider) {
        assertThat(policy.findTypoSuggestion(typo)).contains(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gmail.com", "hotmail.es", "udenar.edu.co", "protonmail.com", "live.com", "gmx.com"})
    void shouldNotFlagRealDomains(String domain) {
        assertThat(policy.findTypoSuggestion(domain)).isEmpty();
    }

    @Test
    void shouldNotQueryTheDnsForATypo() {
        properties.setDnsCheckEnabled(true);

        assertThat(policy.findProblem("ana@gail.com")).isPresent();
        verify(lookup, never()).exists(anyString());
    }

    @Test
    void shouldRejectUnknownDomainsOnlyWhenTheDnsCheckIsEnabled() {
        when(lookup.exists("noexiste.com")).thenReturn(false);

        properties.setDnsCheckEnabled(false);
        assertThat(policy.findProblem("ana@noexiste.com")).isEmpty();

        properties.setDnsCheckEnabled(true);
        assertThat(policy.findProblem("ana@noexiste.com"))
            .contains("Correo inexistente: el dominio «noexiste.com» no existe. Revisa tu correo.");
    }

    @Test
    void shouldCountTranspositionsAsOneEdit() {
        assertThat(EmailDomainPolicy.editDistance("gmial.com", "gmail.com")).isEqualTo(1);
        assertThat(EmailDomainPolicy.editDistance("gmail.com", "gmail.com")).isZero();
        assertThat(EmailDomainPolicy.editDistance("yahoo.es", "yahoo.com")).isEqualTo(3);
    }

    private static MessageResolver messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new MessageResolver(source);
    }
}
