package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a participant submits a join request for a meeting with MANUAL_APPROVAL policy.
 */
public record JoinRequestCreatedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        UUID joinRequestId,
        String accountId,
        String displayName,
        String deviceId,
        Instant occurredAt)
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
        return "io.github.smiskinext.meet.join-request.created.v1";
    }

    @Override
    public String topic() {
        return "meet.join-request.created";
    }
}
