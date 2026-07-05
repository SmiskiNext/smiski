package io.github.smiskinext.meetingmanagement.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.util.Objects;

/**
 * Meeting title — optional, max 255 characters.
 *
 * <p>Wraps a non-blank string. Callers should use {@link #of(String)} for raw input
 * and hold this as {@code @Nullable MeetingTitle} on the aggregate.
 */
public record MeetingTitle(String value) implements ValueObject {

    public static final int MAX_LENGTH = 255;

    public MeetingTitle {
        Objects.requireNonNull(value, "MeetingTitle value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("MeetingTitle must not be blank");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "MeetingTitle must not exceed " + MAX_LENGTH + " characters");
    }

    public static MeetingTitle of(String raw) {
        return new MeetingTitle(raw.strip());
    }
}
