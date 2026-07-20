package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an invitee accepts a meeting invitation.
 */
public record InviteeAcceptedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String inviterId,
        UUID inviteeId,
        String inviteeEmail,
        String status,
        Instant acceptedAt)
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
