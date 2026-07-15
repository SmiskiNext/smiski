package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a join request expires due to TTL elapsing.
 */
public record JoinRequestExpiredEvent(
        UUID eventId, String tenantId, UUID meetingId, UUID joinRequestId, Instant occurredAt)
        implements PublishableEvent {

    @Override
    public String aggregateId() {
        return meetingId.toString();
    }

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.join-request.expired.v1";
    }

    @Override
    public String topic() {
        return "meet.join-request.expired";
    }
}
