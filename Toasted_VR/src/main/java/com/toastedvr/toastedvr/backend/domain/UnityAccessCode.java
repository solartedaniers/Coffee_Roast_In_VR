package com.toastedvr.toastedvr.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "unity_access_codes")
public class UnityAccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String lookupHash;

    @Column(nullable = false, columnDefinition = "text")
    private String encryptedCode;

    @Column(nullable = false, length = 32)
    private String encryptionIv;

    @Column(nullable = false)
    private int encryptionKeyVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime regeneratedAt;

    private LocalDateTime lastEmailedAt;

    protected UnityAccessCode() {
    }

    public UnityAccessCode(
        User user,
        String lookupHash,
        String encryptedCode,
        String encryptionIv,
        int encryptionKeyVersion,
        LocalDateTime createdAt
    ) {
        this.user = user;
        this.lookupHash = lookupHash;
        this.encryptedCode = encryptedCode;
        this.encryptionIv = encryptionIv;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLookupHash() {
        return lookupHash;
    }

    public String getEncryptedCode() {
        return encryptedCode;
    }

    public String getEncryptionIv() {
        return encryptionIv;
    }

    public int getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRegeneratedAt() {
        return regeneratedAt;
    }

    public LocalDateTime getLastEmailedAt() {
        return lastEmailedAt;
    }

    public void replaceCode(String lookupHash, String encryptedCode, String encryptionIv, int encryptionKeyVersion) {
        this.lookupHash = lookupHash;
        this.encryptedCode = encryptedCode;
        this.encryptionIv = encryptionIv;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.regeneratedAt = LocalDateTime.now();
    }

    public void markEmailed() {
        this.lastEmailedAt = LocalDateTime.now();
    }
}
