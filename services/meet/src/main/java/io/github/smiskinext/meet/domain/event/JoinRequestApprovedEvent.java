package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a host approves a pending join request.
 *
 * <p>Carries the requester's {@code accountId} and {@code deviceId} so a device-scoped client and
 * the requester-facing SSE stream (keyed by {@code joinRequestId}) can correlate the outcome, plus
 * the issued LiveKit token and room name the requester needs to connect.
 */
public record JoinRequestApprovedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        UUID joinRequestId,
        String accountId,
        String deviceId,
        String liveKitToken,
        String roomName,
        String approvedBy,
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
        return "io.github.smiskinext.meet.join.approved.v1";
    }

    @Override
    public String topic() {
        return "meet.join.approved";
    }
}
