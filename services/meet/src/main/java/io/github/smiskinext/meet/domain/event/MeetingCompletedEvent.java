package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a meeting transitions RUNNING → COMPLETED.
 */
public record MeetingCompletedEvent(
        UUID eventId, String tenantId, UUID meetingId, String hostId, Instant completedAt)
        implements PublishableEvent {

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
        return "io.github.smiskinext.meet.meeting.completed.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.completed";
    }

    @Override
    public Instant occurredAt() {
        return completedAt;
    }
}
