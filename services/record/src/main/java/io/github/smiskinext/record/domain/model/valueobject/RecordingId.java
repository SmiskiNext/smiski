package io.github.smiskinext.record.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed wrapper for the {@code recordings.id} UUID primary key.
 *
 * <p>Prevents accidental mixing with other UUID-typed identifiers such as
 * {@link MeetingId}.
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
