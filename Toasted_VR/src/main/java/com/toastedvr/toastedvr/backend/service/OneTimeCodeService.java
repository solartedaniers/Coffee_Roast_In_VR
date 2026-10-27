package com.toastedvr.toastedvr.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;
import com.toastedvr.toastedvr.backend.domain.OneTimeCode;
import com.toastedvr.toastedvr.backend.domain.OneTimeCodePurpose;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.exception.ErrorCode;
import com.toastedvr.toastedvr.backend.exception.InvalidVerificationCodeException;
import com.toastedvr.toastedvr.backend.repository.OneTimeCodeRepository;
import com.toastedvr.toastedvr.backend.security.TokenHasher;
import org.springframework.stereotype.Service;

// Reglas de los códigos de un solo uso: vencimiento, intentos por código y
// límite de reenvíos con bloqueo temporal. El código solo se guarda como hash.
// Los llamadores deben evitar el rollback ante InvalidVerificationCodeException
// para que los intentos fallidos y los bloqueos queden registrados.
@Service
public class OneTimeCodeService {

    private final OneTimeCodeRepository oneTimeCodeRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final TokenHasher tokenHasher;
    private final OneTimeCodeProperties properties;
    private final MessageResolver messages;

    public OneTimeCodeService(
        OneTimeCodeRepository oneTimeCodeRepository,
        VerificationCodeGenerator verificationCodeGenerator,
        TokenHasher tokenHasher,
        OneTimeCodeProperties properties,
        MessageResolver messages
    ) {
        this.oneTimeCodeRepository = oneTimeCodeRepository;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.messages = messages;
    }

    /** Emite el primer código de un flujo y reinicia el contador de reenvíos. Devuelve el código en claro. */
    public String issue(User user, OneTimeCodePurpose purpose) {
        OneTimeCode oneTimeCode = findOrCreate(user, purpose);
        oneTimeCode.resetResends();
        return replaceCode(oneTimeCode);
    }

    /**
     * Primer código si el flujo no tiene uno; si ya existe, aplica las reglas de
     * reenvío. Evita que pedir el código otra vez reinicie el límite de reenvíos.
     */
    public String issueOrResend(User user, OneTimeCodePurpose purpose) {
        return oneTimeCodeRepository.findByUserAndPurpose(user, purpose).isPresent()
            ? resend(user, purpose)
            : issue(user, purpose);
    }

    /** Emite un código nuevo solo si el anterior ya no sirve y no se superó el límite de reenvíos. */
    public String resend(User user, OneTimeCodePurpose purpose) {
        OneTimeCode oneTimeCode = findOrCreate(user, purpose);
        LocalDateTime now = LocalDateTime.now();

        if (oneTimeCode.isResendLockedAt(now)) {
            throw tooManyResends(oneTimeCode.getResendLockedUntil(), now);
        }

        if (oneTimeCode.hasResendLock()) {
            oneTimeCode.resetResends();
        }

        if (oneTimeCode.isActiveAt(now)) {
            throw new InvalidVerificationCodeException(
                ErrorCode.CODE_STILL_ACTIVE,
                messages.get("verification.code.stillActive"),
                Map.of("secondsRemaining", secondsBetween(now, oneTimeCode.getExpiresAt()))
            );
        }

        if (oneTimeCode.getResendCount() >= properties.getMaxResends()) {
            LocalDateTime lockedUntil = now.plusMinutes(properties.getResendLockMinutes());
            oneTimeCode.lockResendsUntil(lockedUntil);
            oneTimeCodeRepository.save(oneTimeCode);
            throw tooManyResends(lockedUntil, now);
        }

        oneTimeCode.registerResend();
        return replaceCode(oneTimeCode);
    }

    /** Valida el código y lo consume. Cada fallo descuenta un intento; al agotarlos el código se invalida. */
    public void verify(User user, OneTimeCodePurpose purpose, String code) {
        OneTimeCode oneTimeCode = oneTimeCodeRepository.findByUserAndPurpose(user, purpose)
            .orElseThrow(this::expired);

        if (oneTimeCode.isInvalidated()) {
            throw attemptsExhausted();
        }

        if (oneTimeCode.isExpiredAt(LocalDateTime.now())) {
            throw expired();
        }

        if (!matches(code, oneTimeCode.getCodeHash())) {
            int failedAttempts = oneTimeCode.registerFailedAttempt();
            int remainingAttempts = properties.getMaxAttempts() - failedAttempts;

            if (remainingAttempts <= 0) {
                oneTimeCode.invalidate();
                oneTimeCodeRepository.save(oneTimeCode);
                throw attemptsExhausted();
            }

            oneTimeCodeRepository.save(oneTimeCode);
            throw new InvalidVerificationCodeException(
                ErrorCode.INVALID_CODE,
                messages.get("verification.code.invalid", remainingAttempts),
                Map.of("remainingAttempts", remainingAttempts)
            );
        }

        oneTimeCodeRepository.delete(oneTimeCode);
    }

    private OneTimeCode findOrCreate(User user, OneTimeCodePurpose purpose) {
        return oneTimeCodeRepository.findByUserAndPurpose(user, purpose)
            .orElseGet(() -> new OneTimeCode(user, purpose));
    }

    private String replaceCode(OneTimeCode oneTimeCode) {
        String code = verificationCodeGenerator.generate();
        oneTimeCode.replaceCode(
            tokenHasher.hash(code),
            LocalDateTime.now().plusMinutes(properties.getExpirationMinutes())
        );
        oneTimeCodeRepository.save(oneTimeCode);
        return code;
    }

    private boolean matches(String code, String expectedHash) {
        return code != null && MessageDigest.isEqual(
            tokenHasher.hash(code).getBytes(StandardCharsets.UTF_8),
            expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private InvalidVerificationCodeException expired() {
        return new InvalidVerificationCodeException(ErrorCode.CODE_EXPIRED, messages.get("verification.code.expired"), null);
    }

    private InvalidVerificationCodeException attemptsExhausted() {
        return new InvalidVerificationCodeException(
            ErrorCode.CODE_ATTEMPTS_EXHAUSTED,
            messages.get("verification.code.attemptsExhausted"),
            null
        );
    }

    private InvalidVerificationCodeException tooManyResends(LocalDateTime lockedUntil, LocalDateTime now) {
        return new InvalidVerificationCodeException(
            ErrorCode.TOO_MANY_RESENDS,
            messages.get("verification.code.tooManyResends"),
            Map.of("secondsRemaining", secondsBetween(now, lockedUntil))
        );
    }

    private long secondsBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toSeconds());
    }
}
