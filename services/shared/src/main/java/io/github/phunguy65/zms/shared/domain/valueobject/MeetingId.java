package io.github.phunguy65.zms.shared.domain.valueobject;

import io.github.phunguy65.zms.shared.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a strongly-typed meeting identifier.
 * Wraps a {@link UUID} to prevent accidental mixing with other aggregate IDs.
 */
public record MeetingId(UUID value) implements ValueObject {

    public MeetingId {
        Objects.requireNonNull(value, "MeetingId must not be null");
    }

    public static MeetingId of(UUID value) {
        return new MeetingId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
