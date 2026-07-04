package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.phunguy65.zms.shared.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TemporaryToken(
        String value, UserId userId, TemporaryTokenPurpose purpose, Instant expiresAt)
        implements io.github.phunguy65.zms.shared.domain.ValueObject {

    public TemporaryToken {
        Objects.requireNonNull(value, "TemporaryToken value must not be null");
        Objects.requireNonNull(userId, "TemporaryToken userId must not be null");
        Objects.requireNonNull(purpose, "TemporaryToken purpose must not be null");
        Objects.requireNonNull(expiresAt, "TemporaryToken expiresAt must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("TemporaryToken value must not be blank");
        }
    }

    public static TemporaryToken of(
            String value, UserId userId, TemporaryTokenPurpose purpose, Instant expiresAt) {
        if (expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("TemporaryToken must not be expired");
        }
        return new TemporaryToken(value, userId, purpose, expiresAt);
    }

    public Duration timeToLive() {
        return Duration.between(Instant.now(), expiresAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
