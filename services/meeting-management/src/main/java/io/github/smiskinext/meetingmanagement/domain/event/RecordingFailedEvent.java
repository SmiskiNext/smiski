package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a recording transitions to FAILED.
 */
public record RecordingFailedEvent(UUID eventId, UUID aggregateId, UUID meetingId, Instant failedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "recording";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.recording.failed.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.recording.failed";
    }

    @Override
    public Instant occurredAt() {
        return failedAt;
    }
}
