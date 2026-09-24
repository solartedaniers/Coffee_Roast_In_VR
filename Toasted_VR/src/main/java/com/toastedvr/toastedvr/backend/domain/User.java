package com.toastedvr.toastedvr.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(columnDefinition = "text")
    private String profileImageUrl;

    @Column(nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'PLAYER'")
    private Role role;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(length = 10)
    private String verificationCode;

    private LocalDateTime verificationCodeExpiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private KnowledgeLevel knowledgeLevel;

    // Solo se guarda el hash SHA-256 del refresh token; la columna conserva su nombre.
    @Column(name = "refresh_token", length = 512)
    private String refreshTokenHash;

    private LocalDateTime refreshTokenExpiresAt;

    protected User() {
    }

    public User(String name, String email, String username, String password) {
        this.name = name;
        this.email = email;
        this.username = username;
        this.password = password;
        this.emailVerified = false;
        this.role = Role.PLAYER;
        this.enabled = true;
    }

    @PrePersist
    public void prePersist() {
        if (role == null) {
            role = Role.PLAYER;
        }

        if (enabled == null) {
            enabled = true;
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Role getRole() {
        return role != null ? role : Role.PLAYER;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public LocalDateTime getVerificationCodeExpiresAt() {
        return verificationCodeExpiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void updateProfile(String name, String username, String profileImageUrl) {
        this.name = name;
        this.username = username;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateVerificationCode(String verificationCode, LocalDateTime expiresAt) {
        this.verificationCode = verificationCode;
        this.verificationCodeExpiresAt = expiresAt;
        this.emailVerified = false;
    }

    public void markEmailAsVerified() {
        this.emailVerified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
    }

    public void assignRole(Role role) {
        this.role = role;
    }

    public void block() {
        this.enabled = false;
    }

    public void activate() {
        this.enabled = true;
    }

    public void updateLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public KnowledgeLevel getKnowledgeLevel() {
        return knowledgeLevel;
    }

    public void updateKnowledgeLevel(KnowledgeLevel knowledgeLevel) {
        this.knowledgeLevel = knowledgeLevel;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void updateRefreshTokenHash(String refreshTokenHash, LocalDateTime expiresAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenExpiresAt = expiresAt;
    }

    public void clearRefreshToken() {
        this.refreshTokenHash = null;
        this.refreshTokenExpiresAt = null;
    }
}
