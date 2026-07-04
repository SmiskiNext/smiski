package io.github.smiskinext.usermanagement.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_timestamp")
    private Instant lastAttemptTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetTokenJpaEntity() {}

    public PasswordResetTokenJpaEntity(
            UUID id,
            UUID userId,
            String otpHash,
            Instant expiresAt,
            Instant usedAt,
            int attempts,
            Instant lastAttemptTimestamp,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.attempts = attempts;
        this.lastAttemptTimestamp = lastAttemptTimestamp;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLastAttemptTimestamp() {
        return lastAttemptTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public void setLastAttemptTimestamp(Instant lastAttemptTimestamp) {
        this.lastAttemptTimestamp = lastAttemptTimestamp;
    }
}
