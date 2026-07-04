package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a join request expires due to TTL elapsing.
 */
public record JoinRequestExpiredEvent(
        UUID eventId, UUID meetingId, UUID joinRequestId, Instant occurredAt)
        implements PublishableEvent {

    @Override
    public UUID aggregateId() {
        return meetingId;
    }

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.meeting.join-request.expired.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.join-request.expired";
    }
}
