package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;
import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting is soft-deleted by its host.
 *
 * <p>Carries a full aggregate snapshot plus the account that performed the deletion and the
 * deletion timestamp, mirroring {@code MeetingCreatedEvent} and {@code MeetingStartedEvent}, so
 * downstream consumers can react to removals with the complete meeting state.
 */
public record MeetingDeletedEvent(
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
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence,
        Instant createdAt,
        String deletedBy,
        Instant deletedAt)
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
        return "io.github.smiskinext.meet.meeting.deleted.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.deleted";
    }

    @Override
    public Instant occurredAt() {
        return deletedAt;
    }
}
