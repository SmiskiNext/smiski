package io.github.smiskinext.record.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed wrapper for the {@code recordings.id} UUID primary key.
 */
public record RecordingId(UUID value) implements ValueObject {

    public RecordingId {
        Objects.requireNonNull(value, "RecordingId must not be null");
    }

    public static RecordingId of(UUID value) {
        return new RecordingId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
