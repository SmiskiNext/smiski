package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a host successfully removes one or more active sessions of a participant from a
 * live meeting.
 */
public record ParticipantKickedEvent(
        UUID eventId,
        UUID meetingId,
        String kickedBy,
        @Nullable String kickedAccountId,
        @Nullable String kickedDisplayName,
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
        return "io.github.smiskinext.meet.participant.kicked.v1";
    }

    @Override
    public String topic() {
        return "meet.participant.kicked";
    }
}
