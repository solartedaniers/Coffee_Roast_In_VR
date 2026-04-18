package com.toastedvr.toastedvr.backend.service;

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
    private final int codeExpirationMinutes;

    public EmailService(
        JavaMailSender mailSender,
        @Value("${app.mail.enabled:false}") boolean mailEnabled,
        @Value("${app.mail.from:no-reply@toastedvr.local}") String senderEmail,
        @Value("${app.verification.code-expiration-minutes:2}") int codeExpirationMinutes
    ) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.senderEmail = senderEmail;
        this.codeExpirationMinutes = codeExpirationMinutes;
    }

    public void sendVerificationCode(String recipientEmail, String recipientName, String verificationCode) {
        if (!mailEnabled) {
            throw new EmailDeliveryException(
                "El envio de correo esta desactivado. Activa MAIL_ENABLED y configura SMTP en el archivo .env.",
                null
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Codigo de Verificacion - Toasted VR");
        message.setText(buildMessage(recipientName, verificationCode));

        try {
            mailSender.send(message);
        } catch (Exception exception) {
            throw new EmailDeliveryException(
                "No fue posible enviar el codigo de verificacion. Revisa la configuracion SMTP.",
                exception
            );
        }
    }

    private String buildMessage(String recipientName, String verificationCode) {
        return """
            Hola %s,

            Tu codigo de verificacion para Toasted VR es: %s

            Este codigo vence en %d minutos.
            Si no solicitaste esta cuenta, puedes ignorar este mensaje.
            """.formatted(recipientName, verificationCode, codeExpirationMinutes);
    }
}
