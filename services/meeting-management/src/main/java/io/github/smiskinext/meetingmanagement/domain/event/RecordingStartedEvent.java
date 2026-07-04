package io.github.smiskinext.meetingmanagement.domain.event;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a recording starts (meeting goes LIVE and recording is initiated).
 */
public record RecordingStartedEvent(
        UUID eventId, UUID aggregateId, UUID meetingId, Instant startedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "recording";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.recording.started.v1";
    }

    @Override
    public String topic() {
        return "meeting-management.recording.started";
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
