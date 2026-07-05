package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a strongly-typed refresh token identifier.
 * Wraps a {@link UUID} to prevent accidental mixing with other aggregate IDs.
 */
public record RefreshTokenId(UUID value) implements ValueObject {

    public RefreshTokenId {
        Objects.requireNonNull(value, "RefreshTokenId must not be null");
    }

    public static RefreshTokenId of(UUID value) {
        return new RefreshTokenId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
