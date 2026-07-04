package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a recording transitions PROCESSING → COMPLETED with file URL available.
 */
public record RecordingCompletedEvent(
        UUID eventId,
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
        return "io.github.phunguy65.zms.recording.completed.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.recording.completed";
    }

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
