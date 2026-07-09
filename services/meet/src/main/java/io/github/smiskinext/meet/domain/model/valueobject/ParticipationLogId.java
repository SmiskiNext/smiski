package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed wrapper for the {@code participation_logs.id} UUIDv7 primary key.
 *
 * <p>Using a value object prevents accidental mixing of this UUID with other
 * UUID-typed identifiers in the domain.
 */
public record ParticipationLogId(UUID value) implements ValueObject {

    public ParticipationLogId {
        Objects.requireNonNull(value, "ParticipationLogId must not be null");
    }

    public static ParticipationLogId of(UUID value) {
        return new ParticipationLogId(value);
    }
}
