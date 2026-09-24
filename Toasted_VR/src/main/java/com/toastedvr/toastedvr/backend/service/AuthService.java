package com.toastedvr.toastedvr.backend.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;
import com.toastedvr.toastedvr.backend.domain.OneTimeCodePurpose;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.CodeSentResponse;
import com.toastedvr.toastedvr.backend.dto.EmailRequest;
import com.toastedvr.toastedvr.backend.dto.AuthenticatedUserResponse;
import com.toastedvr.toastedvr.backend.dto.LoginRequest;
import com.toastedvr.toastedvr.backend.dto.LoginResponse;
import com.toastedvr.toastedvr.backend.dto.LogoutResponse;
import com.toastedvr.toastedvr.backend.dto.OneTimeCodePolicyResponse;
import com.toastedvr.toastedvr.backend.dto.RefreshTokenRequest;
import com.toastedvr.toastedvr.backend.dto.RefreshTokenResponse;
import com.toastedvr.toastedvr.backend.dto.RegisterUserRequest;
import com.toastedvr.toastedvr.backend.dto.RegisterUserResponse;
import com.toastedvr.toastedvr.backend.dto.UserResponse;
import com.toastedvr.toastedvr.backend.dto.VerifyEmailRequest;
import com.toastedvr.toastedvr.backend.exception.AccountBlockedException;
import com.toastedvr.toastedvr.backend.exception.AuthenticationFailedException;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.EmailNotVerifiedException;
import com.toastedvr.toastedvr.backend.exception.InvalidVerificationCodeException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeCodeService oneTimeCodeService;
    private final OneTimeCodeProperties oneTimeCodeProperties;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final UnityAccessCodeService unityAccessCodeService;
    private final MessageResolver messages;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        OneTimeCodeService oneTimeCodeService,
        OneTimeCodeProperties oneTimeCodeProperties,
        EmailService emailService,
        JwtService jwtService,
        TokenBlacklistService tokenBlacklistService,
        RefreshTokenService refreshTokenService,
        AuditService auditService,
        UnityAccessCodeService unityAccessCodeService,
        MessageResolver messages
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeCodeService = oneTimeCodeService;
        this.oneTimeCodeProperties = oneTimeCodeProperties;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.unityAccessCodeService = unityAccessCodeService;
        this.messages = messages;
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

        userRepository.save(user);
        String verificationCode = oneTimeCodeService.issue(user, OneTimeCodePurpose.EMAIL_VERIFICATION);
        emailService.sendVerificationCode(user.getEmail(), user.getName(), verificationCode);

        return new RegisterUserResponse(
            messages.get("auth.register.codeSent"),
            user.getEmail(),
            oneTimeCodeProperties.getExpirationMinutes(),
            OneTimeCodePolicyResponse.from(oneTimeCodeProperties)
        );
    }

    // Sin rollback ante un código rechazado: el bloqueo de reenvíos debe quedar guardado.
    @Transactional(dontRollbackOn = InvalidVerificationCodeException.class)
    public CodeSentResponse resendVerificationCode(EmailRequest request) {
        User user = findPendingUser(request.email());
        String verificationCode = oneTimeCodeService.resend(user, OneTimeCodePurpose.EMAIL_VERIFICATION);
        emailService.sendVerificationCode(user.getEmail(), user.getName(), verificationCode);

        return new CodeSentResponse(
            messages.get("auth.register.codeSent"),
            user.getEmail(),
            OneTimeCodePolicyResponse.from(oneTimeCodeProperties)
        );
    }

    // Sin rollback ante un código rechazado: los intentos fallidos deben quedar guardados.
    @Transactional(dontRollbackOn = InvalidVerificationCodeException.class)
    public UserResponse verifyEmail(VerifyEmailRequest request) {
        User user = findPendingUser(request.email());
        oneTimeCodeService.verify(user, OneTimeCodePurpose.EMAIL_VERIFICATION, request.code());

        user.markEmailAsVerified();
        userRepository.save(user);

        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.isEmailVerified(),
            messages.get("auth.verification.success")
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new AuthenticationFailedException(messages.get("auth.login.invalidCredentials")));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationFailedException(messages.get("auth.login.invalidCredentials"));
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(messages.get("auth.login.emailNotVerified"));
        }

        if (!user.isEnabled()) {
            throw new AccountBlockedException(messages.get("auth.account.blocked"));
        }

        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithUnityAccessCode(String code) {
        User user = unityAccessCodeService.findUserByCode(code)
            .orElseThrow(() -> new AuthenticationFailedException("Invalid Unity access code."));

        if (!user.isEmailVerified() || !user.isEnabled()) {
            throw new AuthenticationFailedException("Invalid Unity access code.");
        }

        return createLoginResponse(user);
    }

    private LoginResponse createLoginResponse(User user) {
        String refreshToken = refreshTokenService.issue(user);
        user.updateLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        auditService.logSuccessfulLogin(user.getId(), user.getUsername());

        return new LoginResponse(
            token,
            "Bearer",
            jwtService.getExpiration(token),
            refreshToken,
            AuthenticatedUserResponse.from(user)
        );
    }

    // Sin rollback al rechazar: el refresh token vencido debe quedar borrado.
    @Transactional(dontRollbackOn = AuthenticationFailedException.class)
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        User user = refreshTokenService.findOwner(request.refreshToken())
            .orElseThrow(() -> new AuthenticationFailedException(messages.get("auth.refresh.invalidToken")));

        if (refreshTokenService.isExpired(user)) {
            user.clearRefreshToken();
            userRepository.save(user);
            throw new AuthenticationFailedException(messages.get("auth.refresh.expiredToken"));
        }

        if (!user.isEnabled()) {
            throw new AccountBlockedException(messages.get("auth.account.blocked"));
        }

        String newRefreshToken = refreshTokenService.issue(user);
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String newAccessToken = jwtService.generateToken(principal);

        return new RefreshTokenResponse(
            newAccessToken,
            "Bearer",
            jwtService.getExpiration(newAccessToken),
            newRefreshToken
        );
    }

    @Transactional
    public LogoutResponse logout(String token) {
        Long userId = jwtService.extractUserId(token);
        String username = jwtService.extractUsername(token);
        tokenBlacklistService.blacklistToken(token, jwtService.getExpiration(token));
        userRepository.findById(Objects.requireNonNull(userId)).ifPresent(user -> {
            user.clearRefreshToken();
            userRepository.save(user);
        });
        auditService.logLogout(userId, username);
        return new LogoutResponse(messages.get("auth.logout.success"));
    }

    private User findPendingUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("auth.verification.pendingAccountNotFound")));

        if (user.isEmailVerified()) {
            throw new ConflictException(messages.get("auth.verification.alreadyVerified"));
        }

        return user;
    }

    private void validateUniqueness(String email, String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException(messages.get("auth.register.usernameTaken"), "username");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(messages.get("auth.register.emailTaken"), "email");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
