package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting is created (both INSTANT and SCHEDULED types).
 */
public record MeetingScheduledEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        String hostId,
        String shortCode,
        @Nullable String title,
        @Nullable Instant startTime,
        Instant scheduledAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.scheduled.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.scheduled";
    }

    @Override
    public Instant occurredAt() {
        return scheduledAt;
    }
}
