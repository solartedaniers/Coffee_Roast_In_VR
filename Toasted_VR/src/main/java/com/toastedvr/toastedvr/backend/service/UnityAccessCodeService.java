package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.UnityAccessCode;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.UnityAccessCodeResponse;
import com.toastedvr.toastedvr.backend.dto.UnityAccessCodeStatusResponse;
import com.toastedvr.toastedvr.backend.exception.AuthenticationFailedException;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UnityAccessCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UnityAccessCodeService {

    private final UnityAccessCodeRepository unityAccessCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UnityAccessCodeCryptoService cryptoService;
    private final EmailService emailService;

    public UnityAccessCodeService(
        UnityAccessCodeRepository unityAccessCodeRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UnityAccessCodeCryptoService cryptoService,
        EmailService emailService
    ) {
        this.unityAccessCodeRepository = unityAccessCodeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cryptoService = cryptoService;
        this.emailService = emailService;
    }

    public UnityAccessCodeStatusResponse getStatus(Long userId) {
        return unityAccessCodeRepository.findByUserId(Objects.requireNonNull(userId))
            .map(code -> new UnityAccessCodeStatusResponse(
                true,
                code.getCreatedAt(),
                code.getRegeneratedAt(),
                code.getLastEmailedAt()
            ))
            .orElse(new UnityAccessCodeStatusResponse(false, null, null, null));
    }

    @Transactional
    public UnityAccessCodeResponse create(Long userId, String currentPassword) {
        User user = findUser(userId);
        confirmPassword(user, currentPassword);

        if (unityAccessCodeRepository.findByUserId(user.getId()).isPresent()) {
            throw new ConflictException("A Unity access code already exists for this user.");
        }

        String rawCode = cryptoService.generateCode();
        UnityAccessCodeCryptoService.EncryptedCode encryptedCode = cryptoService.encrypt(rawCode);
        UnityAccessCode accessCode = new UnityAccessCode(
            user,
            cryptoService.lookupHash(rawCode),
            encryptedCode.encryptedCode(),
            encryptedCode.encryptionIv(),
            encryptedCode.encryptionKeyVersion(),
            LocalDateTime.now()
        );
        unityAccessCodeRepository.save(accessCode);

        return toResponse(rawCode, accessCode);
    }

    @Transactional
    public UnityAccessCodeResponse reveal(Long userId, String currentPassword) {
        User user = findUser(userId);
        confirmPassword(user, currentPassword);
        UnityAccessCode accessCode = findAccessCode(user.getId());
        return toResponse(decrypt(accessCode), accessCode);
    }

    @Transactional
    public void email(Long userId, String currentPassword) {
        User user = findUser(userId);
        confirmPassword(user, currentPassword);
        UnityAccessCode accessCode = findAccessCode(user.getId());
        emailService.sendUnityAccessCode(user.getEmail(), user.getName(), cryptoService.formatCode(decrypt(accessCode)));
        accessCode.markEmailed();
        unityAccessCodeRepository.save(accessCode);
    }

    @Transactional
    public UnityAccessCodeResponse regenerate(Long userId, String currentPassword) {
        User user = findUser(userId);
        confirmPassword(user, currentPassword);
        UnityAccessCode accessCode = findAccessCode(user.getId());

        String rawCode = cryptoService.generateCode();
        UnityAccessCodeCryptoService.EncryptedCode encryptedCode = cryptoService.encrypt(rawCode);
        accessCode.replaceCode(
            cryptoService.lookupHash(rawCode),
            encryptedCode.encryptedCode(),
            encryptedCode.encryptionIv(),
            encryptedCode.encryptionKeyVersion()
        );
        unityAccessCodeRepository.save(accessCode);

        return toResponse(rawCode, accessCode);
    }

    public Optional<User> findUserByCode(String submittedCode) {
        String normalizedCode = cryptoService.normalizeCode(submittedCode);
        if (normalizedCode == null) {
            return Optional.empty();
        }

        return unityAccessCodeRepository.findByLookupHash(cryptoService.lookupHash(normalizedCode))
            .map(UnityAccessCode::getUser);
    }

    private User findUser(Long userId) {
        return userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private UnityAccessCode findAccessCode(Long userId) {
        return unityAccessCodeRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No Unity access code exists for this user."));
    }

    private void confirmPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthenticationFailedException("Current password is incorrect.");
        }
    }

    private String decrypt(UnityAccessCode accessCode) {
        return cryptoService.decrypt(
            accessCode.getEncryptedCode(),
            accessCode.getEncryptionIv(),
            accessCode.getEncryptionKeyVersion()
        );
    }

    private UnityAccessCodeResponse toResponse(String rawCode, UnityAccessCode accessCode) {
        return new UnityAccessCodeResponse(
            cryptoService.formatCode(rawCode),
            accessCode.getCreatedAt(),
            accessCode.getRegeneratedAt()
        );
    }
}
