package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a host approves a join request.
 */
public record JoinRequestApprovedEvent(
        UUID eventId,
        UUID meetingId,
        UUID joinRequestId,
        UUID approvedBy,
        String liveKitToken,
        String roomName,
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
        return "io.github.smiskinext.meet.join-request.approved.v1";
    }

    @Override
    public String topic() {
        return "meet.join-request.approved";
    }
}
