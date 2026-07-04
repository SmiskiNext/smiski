package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a host denies a join request or when a meeting ends with pending requests.
 */
public record JoinRequestDeniedEvent(
        UUID eventId,
        UUID meetingId,
        UUID joinRequestId,
        @Nullable UUID deniedBy,
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
        return "io.github.phunguy65.zms.meeting.join-request.denied.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.join-request.denied";
    }
}
