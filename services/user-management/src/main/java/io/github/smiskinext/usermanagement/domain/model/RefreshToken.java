package io.github.smiskinext.usermanagement.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import io.github.smiskinext.usermanagement.domain.model.valueobject.RefreshTokenId;
import java.time.Instant;
import java.util.Optional;

/**
 * RefreshToken aggregate root. Represents a single issued refresh token.
 */
public class RefreshToken extends AggregateRoot<RefreshTokenId> {

    private final RefreshTokenId id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final Instant createdAt;

    private RefreshToken(
            RefreshTokenId id,
            UserId userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
    }

    /** Factory method for issuing a new refresh token. Generates a UUIDv7 primary key. */
    public static RefreshToken issue(UserId userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(
                RefreshTokenId.of(UuidCreator.getTimeOrderedEpoch()),
                userId,
                tokenHash,
                expiresAt,
                null,
                Instant.now());
    }

    /** Reconstitution factory used by the persistence adapter. */
    public static RefreshToken reconstitute(
            RefreshTokenId id,
            UserId userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revokedAt, createdAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    @Override
    public RefreshTokenId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Optional<Instant> getRevokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
