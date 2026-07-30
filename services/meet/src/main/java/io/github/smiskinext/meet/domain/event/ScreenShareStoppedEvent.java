package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a participant stops sharing their screen (webhook: {@code track_unpublished}
 * for a previously published {@code SCREEN_SHARE} track).
 *
 * <p>Implements {@link SseTriggeringEvent} so other participants see the change in real time
 * instead of waiting for the scheduled outbox poll.
 *
 * <p>{@code identity} is the full {@code <accountId>:<deviceId>} LiveKit identity, allowing
 * consumers to distinguish multiple devices of the same account.
 */
public record ScreenShareStoppedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String accountId,
        String identity,
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
        return "io.github.smiskinext.meet.screen_share.stopped.v1";
    }

    @Override
    public String topic() {
        return "meet.screen_share.stopped";
    }
}
