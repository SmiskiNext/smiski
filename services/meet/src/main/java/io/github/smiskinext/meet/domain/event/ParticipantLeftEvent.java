package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a participant leaves a LiveKit room (webhook: {@code participant_left}).
 * Consumed by chat-management to create a system chat message.
 */
public record ParticipantLeftEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String accountId,
        String displayName,
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
        return "io.github.smiskinext.meet.participant.left.v1";
    }

    @Override
    public String topic() {
        return "meet.participant.left";
    }
}
