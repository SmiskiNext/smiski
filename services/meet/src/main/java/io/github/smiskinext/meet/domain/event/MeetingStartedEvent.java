package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a meeting transitions SCHEDULED → LIVE.
 */
public record MeetingStartedEvent(
        UUID eventId, UUID aggregateId, UUID hostId, String liveKitRoomName, Instant startedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.started.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.started";
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
