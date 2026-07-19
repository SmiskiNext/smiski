package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.UUID;

public record MeetingInfoUpdatedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String hostId,
        String updatedBy,
        MeetingStatus meetingStatus,
        MeetingInfoSnapshot oldInfo,
        MeetingInfoSnapshot newInfo,
        Instant updatedAt)
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
        return "meeting.info.update";
    }

    @Override
    public String topic() {
        return "meeting.info.update";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
