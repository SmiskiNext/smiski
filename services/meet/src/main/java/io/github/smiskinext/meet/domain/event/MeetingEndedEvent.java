package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a meeting transitions LIVE → ENDED.
 */
public record MeetingEndedEvent(
        UUID eventId, String tenantId, UUID aggregateId, String hostId, Instant endedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.ended.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.ended";
    }

    @Override
    public Instant occurredAt() {
        return endedAt;
    }
}
