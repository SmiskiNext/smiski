package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a participant submits a join request for a meeting with MANUAL_APPROVAL policy.
 */
public record JoinRequestCreatedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        UUID joinRequestId,
        String accountId,
        String displayName,
        String deviceId,
        @Nullable String avatarUrl,
        Instant occurredAt)
        implements PublishableEvent, SseTriggeringEvent {

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
        return "io.github.smiskinext.meet.join.created.v1";
    }

    @Override
    public String topic() {
        return "meet.join.created";
    }
}
