package com.toastedvr.toastedvr.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Código de un solo uso (verificación de correo o recuperación de contraseña).
// Hay como máximo una fila por usuario y propósito; se reutiliza en cada reenvío.
@Entity
@Table(
    name = "one_time_codes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "purpose"})
)
public class OneTimeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OneTimeCodePurpose purpose;

    // Null cuando el código se invalidó por agotar los intentos.
    @Column(length = 64)
    private String codeHash;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private int resendCount;

    private LocalDateTime resendLockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OneTimeCode() {
    }

    public OneTimeCode(User user, OneTimeCodePurpose purpose) {
        this.user = user;
        this.purpose = purpose;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void replaceCode(String codeHash, LocalDateTime expiresAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
    }

    public void resetResends() {
        this.resendCount = 0;
        this.resendLockedUntil = null;
    }

    public void registerResend() {
        this.resendCount++;
    }

    public void lockResendsUntil(LocalDateTime lockedUntil) {
        this.resendLockedUntil = lockedUntil;
    }

    public int registerFailedAttempt() {
        return ++failedAttempts;
    }

    public void invalidate() {
        this.codeHash = null;
    }

    public boolean isInvalidated() {
        return codeHash == null;
    }

    public boolean isExpiredAt(LocalDateTime moment) {
        return expiresAt == null || !expiresAt.isAfter(moment);
    }

    public boolean isActiveAt(LocalDateTime moment) {
        return !isInvalidated() && !isExpiredAt(moment);
    }

    public boolean isResendLockedAt(LocalDateTime moment) {
        return resendLockedUntil != null && resendLockedUntil.isAfter(moment);
    }

    public boolean hasResendLock() {
        return resendLockedUntil != null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OneTimeCodePurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public int getResendCount() {
        return resendCount;
    }

    public LocalDateTime getResendLockedUntil() {
        return resendLockedUntil;
    }
}
