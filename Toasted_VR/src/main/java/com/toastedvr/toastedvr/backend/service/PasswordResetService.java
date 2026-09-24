package com.toastedvr.toastedvr.backend.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;
import com.toastedvr.toastedvr.backend.domain.OneTimeCodePurpose;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.CodeSentResponse;
import com.toastedvr.toastedvr.backend.dto.EmailRequest;
import com.toastedvr.toastedvr.backend.dto.MessageResponse;
import com.toastedvr.toastedvr.backend.dto.OneTimeCodePolicyResponse;
import com.toastedvr.toastedvr.backend.dto.PasswordResetConfirmRequest;
import com.toastedvr.toastedvr.backend.exception.EmailDeliveryException;
import com.toastedvr.toastedvr.backend.exception.InvalidRequestException;
import com.toastedvr.toastedvr.backend.exception.InvalidVerificationCodeException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Recuperación de contraseña. Nunca revela si un correo existe: pedir el código
// responde siempre lo mismo y un código rechazado da siempre el mismo error.
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final OneTimeCodeService oneTimeCodeService;
    private final OneTimeCodeProperties oneTimeCodeProperties;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MessageResolver messages;

    public PasswordResetService(
        UserRepository userRepository,
        OneTimeCodeService oneTimeCodeService,
        OneTimeCodeProperties oneTimeCodeProperties,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        AuditService auditService,
        MessageResolver messages
    ) {
        this.userRepository = userRepository;
        this.oneTimeCodeService = oneTimeCodeService;
        this.oneTimeCodeProperties = oneTimeCodeProperties;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.messages = messages;
    }

    /**
     * Envía un código solo a cuentas verificadas y activas. Los límites de
     * vigencia y reenvío se aplican en silencio para no delatar la cuenta.
     */
    @Transactional
    public CodeSentResponse requestCode(EmailRequest request) {
        String email = normalizeEmail(request.email());

        findEligibleUser(email).ifPresent(user -> {
            try {
                String code = oneTimeCodeService.issueOrResend(user, OneTimeCodePurpose.PASSWORD_RESET);
                emailService.sendPasswordResetCode(user.getEmail(), user.getName(), code);
            } catch (InvalidVerificationCodeException exception) {
                // Código aún vigente o reenvíos bloqueados: no se envía nada.
            } catch (EmailDeliveryException exception) {
                log.warn("Could not send password reset code to user {}: {}", user.getId(), exception.getMessage());
            }
        });

        return new CodeSentResponse(
            messages.get("auth.passwordReset.requested"),
            email,
            OneTimeCodePolicyResponse.from(oneTimeCodeProperties)
        );
    }

    // Sin rollback ante un código rechazado: los intentos fallidos deben quedar guardados.
    @Transactional(dontRollbackOn = InvalidVerificationCodeException.class)
    public MessageResponse resetPassword(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidRequestException(messages.get("validation.password.mismatch"), "confirmPassword");
        }

        User user = findEligibleUser(normalizeEmail(request.email()))
            .orElseThrow(this::invalidCode);

        try {
            oneTimeCodeService.verify(user, OneTimeCodePurpose.PASSWORD_RESET, request.code());
        } catch (InvalidVerificationCodeException exception) {
            throw invalidCode();
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Cierra todas las sesiones: borra el refresh token y revoca los access tokens emitidos antes.
        user.invalidateSessions(Instant.now());
        userRepository.save(user);
        auditService.logPasswordReset(user.getId());

        return new MessageResponse(messages.get("auth.passwordReset.success"));
    }

    private Optional<User> findEligibleUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .filter(User::isEmailVerified)
            .filter(User::isEnabled);
    }

    private InvalidVerificationCodeException invalidCode() {
        return new InvalidVerificationCodeException(messages.get("auth.passwordReset.invalidCode"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
