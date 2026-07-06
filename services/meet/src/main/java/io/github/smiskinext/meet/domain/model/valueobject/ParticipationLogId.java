package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Strongly-typed wrapper for the {@code participation_logs.id} bigserial primary key.
 *
 * <p>Using a value object prevents accidental mixing of this Long ID with other
 * Long-typed identifiers in the domain.
 */
public record ParticipationLogId(Long value) implements ValueObject {

    public ParticipationLogId {
        Objects.requireNonNull(value, "ParticipationLogId must not be null");
    }

    public static ParticipationLogId of(Long value) {
        return new ParticipationLogId(value);
    }
}
