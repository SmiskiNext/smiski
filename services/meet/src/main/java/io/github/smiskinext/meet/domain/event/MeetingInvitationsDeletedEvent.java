package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when one or more meeting invitees are removed during an invitee-list replacement.
 * Carries the meeting context together with only the removed invitees.
 *
 * @param eventId              unique identifier for this event occurrence
 * @param meetingId            ID of the meeting aggregate
 * @param meetingTitle         human-readable title of the meeting
 * @param meetingShortCode     short alphanumeric code for the meeting join URL
 * @param startTime            scheduled start time, or {@code null} for instant meetings
 * @param endTime              scheduled end time, or {@code null} for instant meetings
 * @param zoneId               host IANA time zone id
 * @param organizerEmail       organizer email at invite time
 * @param organizerDisplayName organizer display name at invite time
 * @param calendarUid          calendar uid of the meeting
 * @param calendarSequence     calendar sequence of the meeting
 * @param invitees             list of removed invitees with display info
 * @param occurredAt           timestamp when the event occurred
 */
public record MeetingInvitationsDeletedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence,
        List<InviteeInfo> invitees,
        Instant occurredAt)
        implements PublishableEvent {

    /**
     * Minimal invitee info needed by the notification service.
     *
     * @param inviteeId   the invitee identity
     * @param accountId   resolved Jira account ID (always present — invitees are frontend-resolved)
     * @param email       the invite target email
     * @param displayName the user's display name at removal time
     * @param status      the invitation status at removal time
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
        return "io.github.smiskinext.meet.meeting.invitations.deleted.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.invitations.deleted";
    }

    @Override
    public String toString() {
        return "MeetingInvitationsDeletedEvent[eventId="
                + eventId
                + ", meetingId="
                + meetingId
                + ", meetingTitle="
                + meetingTitle
                + ", meetingShortCode="
                + meetingShortCode
                + ", startTime="
                + startTime
                + ", endTime="
                + endTime
                + ", zoneId="
                + zoneId
                + ", invitees="
                + invitees
                + ", occurredAt="
                + occurredAt
                + ']';
    }
}
