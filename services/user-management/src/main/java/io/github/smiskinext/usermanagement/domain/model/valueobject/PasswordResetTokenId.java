package io.github.smiskinext.usermanagement.domain.model.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a strongly-typed password reset token identifier.
 * Wraps a {@link UUID} to prevent accidental mixing with other aggregate IDs.
 */
public record PasswordResetTokenId(UUID value) implements ValueObject {

    public PasswordResetTokenId {
        Objects.requireNonNull(value, "PasswordResetTokenId must not be null");
    }

    public static PasswordResetTokenId of(UUID value) {
        return new PasswordResetTokenId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
