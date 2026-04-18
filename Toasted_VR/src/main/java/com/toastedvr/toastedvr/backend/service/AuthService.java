package com.toastedvr.toastedvr.backend.service;

import java.time.LocalDateTime;
import java.util.Locale;

import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.RegisterUserRequest;
import com.toastedvr.toastedvr.backend.dto.RegisterUserResponse;
import com.toastedvr.toastedvr.backend.dto.UserResponse;
import com.toastedvr.toastedvr.backend.dto.VerifyEmailRequest;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.InvalidVerificationCodeException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final EmailService emailService;
    private final int codeExpirationMinutes;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        VerificationCodeGenerator verificationCodeGenerator,
        EmailService emailService,
        @Value("${app.verification.code-expiration-minutes:15}") int codeExpirationMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.emailService = emailService;
        this.codeExpirationMinutes = codeExpirationMinutes;
    }

    @Transactional
    public RegisterUserResponse registerUser(RegisterUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedUsername = request.username().trim();

        validateUniqueness(normalizedEmail, normalizedUsername);

        User user = new User(
            request.name().trim(),
            normalizedEmail,
            normalizedUsername,
            passwordEncoder.encode(request.password())
        );

        String verificationCode = verificationCodeGenerator.generate();
        user.updateVerificationCode(
            verificationCode,
            LocalDateTime.now().plusMinutes(codeExpirationMinutes)
        );

        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getName(), verificationCode);

        return new RegisterUserResponse(
            "Te enviamos un codigo de verificacion a tu correo.",
            user.getEmail(),
            codeExpirationMinutes
        );
    }

    @Transactional
    public UserResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResourceNotFoundException("No existe una cuenta pendiente para ese correo."));

        if (user.isEmailVerified()) {
            throw new ConflictException("Esta cuenta ya fue verificada.");
        }

        if (user.getVerificationCodeExpiresAt() == null
            || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            String newCode = verificationCodeGenerator.generate();
            user.updateVerificationCode(
                newCode,
                LocalDateTime.now().plusMinutes(codeExpirationMinutes)
            );
            userRepository.save(user);
            emailService.sendVerificationCode(user.getEmail(), user.getName(), newCode);
            throw new InvalidVerificationCodeException(
                "El codigo vencio. Te enviamos uno nuevo al correo registrado."
            );
        }

        if (!request.code().equals(user.getVerificationCode())) {
            throw new InvalidVerificationCodeException("El codigo ingresado no es correcto.");
        }

        user.markEmailAsVerified();
        userRepository.save(user);

        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.isEmailVerified(),
            "La cuenta fue verificada y creada correctamente."
        );
    }

    private void validateUniqueness(String email, String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("El nombre de usuario ya esta en uso.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo electronico ya esta registrado.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
