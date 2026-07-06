package io.github.smiskinext.record.domain.event;

import io.github.smiskinext.record.domain.PublishableEvent;

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
        return "io.github.smiskinext.record.recording.started.v1";
    }

    @Override
    public String topic() {
        return "record.recording.started";
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
