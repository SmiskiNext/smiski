package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a meeting transitions LIVE → ENDED.
 */
public record MeetingEndedEvent(UUID eventId, UUID aggregateId, UUID hostId, Instant endedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.meeting.ended.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.meeting.ended";
    }

    @Override
    public Instant occurredAt() {
        return endedAt;
    }
}
