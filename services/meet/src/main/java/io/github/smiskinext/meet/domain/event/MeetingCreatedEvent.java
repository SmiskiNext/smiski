package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting aggregate is created (both scheduled and instant meetings).
 * Carries a full aggregate snapshot for downstream consumers.
 */
public record MeetingCreatedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String hostId,
        String shortCode,
        String type,
        String status,
        String title,
        String description,
        String issueId,
        String issueKey,
        String projectKey,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        MeetingSettings settings,
        Instant createdAt)
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
        return "io.github.smiskinext.meet.meeting.created.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.created";
    }

    @Override
    public Instant occurredAt() {
        return createdAt;
    }
}
