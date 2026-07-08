package io.github.smiskinext.record.domain.event;

import io.github.smiskinext.record.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a recording transitions to FAILED.
 */
public record RecordingFailedEvent(
        UUID eventId, String tenantId, UUID aggregateId, UUID meetingId, Instant failedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "recording";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.record.recording.failed.v1";
    }

    @Override
    public String topic() {
        return "record.recording.failed";
    }

    @Override
    public Instant occurredAt() {
        return failedAt;
    }
}
