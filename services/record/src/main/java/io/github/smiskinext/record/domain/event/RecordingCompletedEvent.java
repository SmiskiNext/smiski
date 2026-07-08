package io.github.smiskinext.record.domain.event;

import io.github.smiskinext.record.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a recording transitions PROCESSING → COMPLETED with file URL available.
 */
public record RecordingCompletedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID meetingId,
        String fileUrl,
        int durationSeconds,
        long fileSizeBytes,
        Instant completedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "recording";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.record.recording.completed.v1";
    }

    @Override
    public String topic() {
        return "record.recording.completed";
    }

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
