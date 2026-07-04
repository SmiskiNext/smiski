package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
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
        return "io.github.phunguy65.zms.meeting.started.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.meeting.started";
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
