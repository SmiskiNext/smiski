package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a host denies a join request or when a meeting ends with pending requests.
 */
public record JoinRequestDeniedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        UUID joinRequestId,
        @Nullable UUID deniedBy,
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
        return "io.github.smiskinext.meet.join-request.denied.v1";
    }

    @Override
    public String topic() {
        return "meet.join-request.denied";
    }
}
