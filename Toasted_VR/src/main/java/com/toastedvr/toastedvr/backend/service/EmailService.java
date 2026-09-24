package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;
import com.toastedvr.toastedvr.backend.exception.EmailDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String senderEmail;
    private final OneTimeCodeProperties oneTimeCodeProperties;
    private final MessageResolver messages;

    public EmailService(
        JavaMailSender mailSender,
        @Value("${app.mail.enabled:false}") boolean mailEnabled,
        @Value("${app.mail.from:no-reply@toastedvr.local}") String senderEmail,
        OneTimeCodeProperties oneTimeCodeProperties,
        MessageResolver messages
    ) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.senderEmail = senderEmail;
        this.oneTimeCodeProperties = oneTimeCodeProperties;
        this.messages = messages;
    }

    public void sendVerificationCode(String recipientEmail, String recipientName, String verificationCode) {
        if (!mailEnabled) {
            throw new EmailDeliveryException(messages.get("email.delivery.disabled"), null);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(messages.get("email.verification.subject"));
        message.setText(buildVerificationMessage(recipientName, verificationCode));

        try {
            mailSender.send(message);
        } catch (Exception exception) {
            throw new EmailDeliveryException(messages.get("email.delivery.verificationFailed"), exception);
        }
    }

    public void sendPasswordResetCode(String recipientEmail, String recipientName, String resetCode) {
        if (!mailEnabled) {
            throw new EmailDeliveryException(messages.get("email.delivery.disabled"), null);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(messages.get("email.passwordReset.subject"));
        message.setText(buildPasswordResetMessage(recipientName, resetCode));

        try {
            mailSender.send(message);
        } catch (Exception exception) {
            throw new EmailDeliveryException(messages.get("email.delivery.passwordResetFailed"), exception);
        }
    }

    public void sendUnityAccessCode(String recipientEmail, String recipientName, String unityAccessCode) {
        if (!mailEnabled) {
            throw new EmailDeliveryException("Email delivery is disabled.", null);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Your Toasted VR Unity Access Code");
        message.setText(buildUnityAccessCodeMessage(recipientName, unityAccessCode));

        try {
            mailSender.send(message);
        } catch (Exception exception) {
            throw new EmailDeliveryException("Unable to send the Unity access code email.", exception);
        }
    }

    String buildVerificationMessage(String recipientName, String verificationCode) {
        return messages.get(
            "email.verification.body",
            recipientName,
            verificationCode,
            oneTimeCodeProperties.getExpirationMinutes()
        );
    }

    String buildPasswordResetMessage(String recipientName, String resetCode) {
        return messages.get(
            "email.passwordReset.body",
            recipientName,
            resetCode,
            oneTimeCodeProperties.getExpirationMinutes()
        );
    }

    private String buildUnityAccessCodeMessage(String recipientName, String unityAccessCode) {
        return """
            Hello %s,

            Your permanent Toasted VR Unity access code is: %s

            Keep this code private. It remains valid until you regenerate it from your profile.
            """.formatted(recipientName, unityAccessCode);
    }
}
