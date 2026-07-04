package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when an invitee declines a meeting invitation.
 */
public record InviteeDeclinedEvent(
        UUID eventId, UUID aggregateId, UUID meetingId, UUID inviterId, Instant declinedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting-invitee";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.meeting-invitee.declined.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.invitee.declined";
    }

    @Override
    public Instant occurredAt() {
        return declinedAt;
    }
}
