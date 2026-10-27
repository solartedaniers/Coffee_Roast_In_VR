package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailServiceTests {

    @Test
    void shouldTellTheConfiguredExpirationInSingular() {
        String body = emailServiceWithExpiration(1).buildVerificationMessage("Ana", "123456");

        assertThat(body)
            .contains("Hola Ana")
            .contains("123456")
            .contains("Este código vence en 1 minuto.");
    }

    @Test
    void shouldTellTheConfiguredExpirationInPlural() {
        String body = emailServiceWithExpiration(3).buildVerificationMessage("Ana", "123456");

        assertThat(body).contains("Este código vence en 3 minutos.");
    }

    @Test
    void shouldWriteThePasswordResetEmailInSpanishWithTheConfiguredExpiration() {
        String body = emailServiceWithExpiration(1).buildPasswordResetMessage("Ana", "654321");

        assertThat(body)
            .contains("Hola Ana")
            .contains("restablecer la contraseña")
            .contains("654321")
            .contains("Este código vence en 1 minuto.")
            .contains("tu contraseña no cambiará");
    }

    @Test
    void shouldExplainUnityCodeExpirationWithoutExtendingIt() {
        String body = emailServiceWithExpiration(1, 10).buildUnityAccessCodeMessage("Ana", "ABCD-2345");

        assertThat(body)
            .contains("ABCD-2345")
            .contains("expires 10 minutes after it was generated")
            .contains("does not extend its expiration time");
    }

    private EmailService emailServiceWithExpiration(int minutes) {
        return emailServiceWithExpiration(minutes, 10);
    }

    private EmailService emailServiceWithExpiration(int minutes, int unityExpirationMinutes) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        OneTimeCodeProperties properties = new OneTimeCodeProperties();
        properties.setExpirationMinutes(minutes);

        return new EmailService(
            mock(JavaMailSender.class),
            true,
            "no-reply@toastedvr.test",
            properties,
            new MessageResolver(messageSource),
            unityExpirationMinutes
        );
    }
}
