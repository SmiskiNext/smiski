package io.github.smiskinext.usermanagement.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.phunguy65.zms.shared.domain.AggregateRoot;
import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.PasswordResetTokenId;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * PasswordResetToken aggregate root. Represents a single OTP-based password reset request.
 *
 * <p>The token stores a SHA-256 hash of the 6-digit OTP (not the OTP itself).
 * It tracks wrong attempts and locks after {@link #MAX_ATTEMPTS} failures.
 */
public class PasswordResetToken extends AggregateRoot<PasswordResetTokenId> {

    /** Maximum wrong OTP attempts before the token is locked. */
    public static final int MAX_ATTEMPTS = 5;

    private final PasswordResetTokenId id;
    private final UserId userId;
    private final String otpHash;
    private final Instant expiresAt;
    private @Nullable Instant usedAt;
    private int attempts;
    private @Nullable Instant lastAttemptTimestamp;
    private final Instant createdAt;

    private PasswordResetToken(
            PasswordResetTokenId id,
            UserId userId,
            String otpHash,
            Instant expiresAt,
            @Nullable Instant usedAt,
            int attempts,
            @Nullable Instant lastAttemptTimestamp,
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

    /**
     * Factory method for issuing a new password reset token.
     * Generates a UUIDv7 primary key.
     *
     * @param userId    the user requesting password reset
     * @param otpHash   SHA-256 hash of the 6-digit OTP
     * @param expiresAt when this token expires
     * @return a new PasswordResetToken instance
     */
    public static PasswordResetToken issue(UserId userId, String otpHash, Instant expiresAt) {
        return new PasswordResetToken(
                PasswordResetTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                userId,
                otpHash,
                expiresAt,
                null,
                0,
                null,
                Instant.now());
    }

    /** Reconstitution factory used by the persistence adapter. */
    public static PasswordResetToken reconstitute(
            PasswordResetTokenId id,
            UserId userId,
            String otpHash,
            Instant expiresAt,
            @Nullable Instant usedAt,
            int attempts,
            @Nullable Instant lastAttemptTimestamp,
            Instant createdAt) {
        return new PasswordResetToken(
                id, userId, otpHash, expiresAt, usedAt, attempts, lastAttemptTimestamp, createdAt);
    }

    /** Returns {@code true} if the token has passed its expiry time. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Returns {@code true} if the token has already been used. */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** Returns {@code true} if too many wrong attempts have been made. */
    public boolean isLocked() {
        return attempts >= MAX_ATTEMPTS;
    }

    /**
     * Returns {@code true} if the token is valid for use:
     * not expired, not used, and not locked.
     */
    public boolean isValid() {
        return !isExpired() && !isUsed() && !isLocked();
    }

    /** Marks this token as used. Should be called after successful password reset. */
    public void markUsed() {
        this.usedAt = Instant.now();
    }

    /**
     * Increments the wrong attempt counter and updates the last attempt timestamp.
     * Call this when OTP verification fails.
     */
    public void incrementAttempts() {
        this.attempts++;
        this.lastAttemptTimestamp = Instant.now();
    }

    @Override
    public PasswordResetTokenId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Optional<Instant> getUsedAt() {
        return Optional.ofNullable(usedAt);
    }

    public int getAttempts() {
        return attempts;
    }

    public Optional<Instant> getLastAttemptTimestamp() {
        return Optional.ofNullable(lastAttemptTimestamp);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
