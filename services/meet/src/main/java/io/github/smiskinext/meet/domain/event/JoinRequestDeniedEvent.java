package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a host denies a pending join request.
 *
 * <p>Carries the requester's {@code accountId} and {@code deviceId} so a device-scoped client and
 * the requester-facing SSE stream (keyed by {@code joinRequestId}) can correlate the outcome.
 */
public record JoinRequestDeniedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        UUID joinRequestId,
        String accountId,
        String deviceId,
        String deniedBy,
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
        return "io.github.smiskinext.meet.join.denied.v1";
    }

    @Override
    public String topic() {
        return "meet.join.denied";
    }
}
