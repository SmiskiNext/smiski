package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.shared.domain.PublishableEvent;

import java.time.Instant;
import java.util.List;
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
        List<InviteeInfo> invitees,
        Instant updatedAt)
        implements PublishableEvent {

    /**
     * Minimal invitee info needed by the notification service for sending update emails.
     *
     * @param inviteeId   the invitee's identity
     * @param accountId   resolved Jira account ID
     * @param email       the invitee's email address
     * @param displayName the user's display name
     * @param status      participation status name (e.g. {@code NEEDS_ACTION})
     */
    public record InviteeInfo(
            UUID inviteeId, String accountId, String email, String displayName, String status) {}

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
        return "io.github.smiskinext.meet.meeting.info.updated.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.info.updated";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
