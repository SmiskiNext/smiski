package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a participant leaves a LiveKit room (webhook: {@code participant_left}).
 * Consumed by chat-management to create a system chat message.
 *
 * <p>{@code identity} is the full {@code <accountId>:<deviceId>} LiveKit identity, allowing
 * consumers to distinguish multiple devices of the same account.
 */
public record ParticipantLeftEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String accountId,
        String identity,
        Instant occurredAt)
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
        return "io.github.smiskinext.meet.participant.left.v1";
    }

    @Override
    public String topic() {
        return "meet.participant.left";
    }
}
