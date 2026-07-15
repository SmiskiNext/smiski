package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an invitee declines a meeting invitation.
 */
public record InviteeDeclinedEvent(
        UUID eventId, String tenantId, UUID meetingId, String inviterId, Instant declinedAt)
        implements PublishableEvent {

    @Override
    public String aggregateId() {
        return meetingId.toString();
    }

    @Override
    public String aggregateType() {
        return "meeting-invitee";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.invitee.declined.v1";
    }

    @Override
    public String topic() {
        return "meet.invitee.declined";
    }

    @Override
    public Instant occurredAt() {
        return declinedAt;
    }
}
