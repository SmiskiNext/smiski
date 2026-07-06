package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable time range for a scheduled meeting.
 *
 * <p>Enforces the invariant that {@code start} is strictly before {@code end},
 * keeping duration validation inside the domain rather than the application layer.
 */
public record MeetingTimeRange(Instant start, Instant end) implements ValueObject {

    public MeetingTimeRange {
        Objects.requireNonNull(start, "MeetingTimeRange start must not be null");
        Objects.requireNonNull(end, "MeetingTimeRange end must not be null");
        if (!start.isBefore(end))
            throw new IllegalArgumentException(
                    "MeetingTimeRange start must be strictly before end");
    }

    /**
     * Duration of the meeting.
     */
    public Duration duration() {
        return Duration.between(start, end);
    }

    public static MeetingTimeRange of(Instant start, Instant end) {
        return new MeetingTimeRange(start, end);
    }
}
