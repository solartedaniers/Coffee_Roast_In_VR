package com.toastedvr.toastedvr.backend.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.service.RankingPhoto;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// "Firma" del ranking mostrado (RF017): la foto exacta que vio el jugador,
// cifrada y autenticada con AES-GCM. Así "marcar como visto" guarda justo lo
// que se mostró aunque el ranking haya cambiado después, los ids de usuario
// no quedan legibles para el cliente y una firma alterada o de otro jugador
// se rechaza. La clave se deriva del secreto JWT solo para este uso.
@Service
public class RankingSignatureService {

    private static final byte[] KEY_PURPOSE = ":ranking-snapshot".getBytes(StandardCharsets.UTF_8);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public RankingSignatureService(@Value("${app.jwt.secret}") String secret, ObjectMapper objectMapper) {
        this.key = new SecretKeySpec(deriveKey(Decoders.BASE64.decode(secret)), "AES");
        this.objectMapper = objectMapper;
    }

    public String sign(Long userId, KnowledgeLevel level, RankingPhoto photo) {
        try {
            byte[] plain = objectMapper.writeValueAsBytes(
                new SignedPhoto(userId, level, photo.topPositions(), photo.ownPosition())
            );
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()
            );
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new IllegalStateException("Unable to sign ranking snapshot.", exception);
        }
    }

    /** Foto firmada si la firma es auténtica y pertenece a este jugador. */
    public Optional<SignedPhoto> verify(String signature, Long userId) {
        try {
            byte[] data = Base64.getUrlDecoder().decode(signature);
            if (data.length <= IV_LENGTH_BYTES) {
                return Optional.empty();
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH_BYTES));
            byte[] plain = cipher.doFinal(data, IV_LENGTH_BYTES, data.length - IV_LENGTH_BYTES);
            SignedPhoto signed = objectMapper.readValue(plain, SignedPhoto.class);
            return signed.userId().equals(userId) ? Optional.of(signed) : Optional.empty();
        } catch (GeneralSecurityException | IllegalArgumentException | IOException exception) {
            return Optional.empty();
        }
    }

    private static byte[] deriveKey(byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            return digest.digest(KEY_PURPOSE);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive ranking signature key.", exception);
        }
    }

    public record SignedPhoto(Long userId, KnowledgeLevel level, Map<Long, Integer> topPositions, Integer ownPosition) {

        public RankingPhoto photo() {
            return new RankingPhoto(topPositions, ownPosition);
        }
    }
}
