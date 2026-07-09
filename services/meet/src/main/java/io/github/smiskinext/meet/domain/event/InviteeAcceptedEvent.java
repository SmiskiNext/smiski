package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an invitee accepts a meeting invitation.
 */
public record InviteeAcceptedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID meetingId,
        String inviterId,
        Instant acceptedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting-invitee";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.invitee.accepted.v1";
    }

    @Override
    public String topic() {
        return "meet.invitee.accepted";
    }

    @Override
    public Instant occurredAt() {
        return acceptedAt;
    }
}
