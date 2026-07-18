package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting transitions SCHEDULED -> RUNNING.
 * Carries a full aggregate snapshot plus the LiveKit room name.
 */
public record MeetingStartedEvent(
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
        String zoneId,
        Instant createdAt,
        String liveKitRoomName,
        Instant startedAt)
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
        return "io.github.smiskinext.meet.meeting.started.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.started";
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
