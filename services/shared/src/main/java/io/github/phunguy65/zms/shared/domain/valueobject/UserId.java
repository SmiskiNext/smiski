package io.github.phunguy65.zms.shared.domain.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a strongly-typed user identifier.
 * Wraps a {@link UUID} to prevent accidental mixing with other aggregate IDs.
 */
public record UserId(UUID value) implements ValueObject {

    public UserId {
        Objects.requireNonNull(value, "UserId must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
