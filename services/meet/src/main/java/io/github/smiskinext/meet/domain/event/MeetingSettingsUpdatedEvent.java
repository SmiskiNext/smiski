package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingSettings;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a meeting's settings are updated by the host.
 *
 * <p>Carries both the previous ({@code oldSettings}) and new ({@code newSettings}) settings
 * snapshots to enable downstream handlers to detect permission-relevant changes without
 * requiring a separate repository lookup.
 */
public record MeetingSettingsUpdatedEvent(
        UUID eventId,
        UUID aggregateId,
        UUID hostId,
        UUID updatedBy,
        MeetingStatus meetingStatus,
        MeetingSettings oldSettings,
        MeetingSettings newSettings,
        Instant updatedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.settings-updated.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.settings-updated";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
