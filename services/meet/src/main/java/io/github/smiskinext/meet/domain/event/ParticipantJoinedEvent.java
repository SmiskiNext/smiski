package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a participant successfully joins a LiveKit room (webhook: {@code participant_joined}).
 * Consumed by chat-management to create a system chat message.
 */
public record ParticipantJoinedEvent(
        UUID eventId, UUID meetingId, @Nullable UUID userId, String displayName, Instant occurredAt)
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
        return "io.github.smiskinext.meet.participant.joined.v1";
    }

    @Override
    public String topic() {
        return "meet.participant.joined";
    }
}
