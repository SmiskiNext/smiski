package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting is scheduled with a non-empty invitee list.
 * Carries enough information for the notification service to send invitation emails.
 *
 * @param eventId          unique identifier for this event occurrence
 * @param meetingId        ID of the meeting aggregate
 * @param meetingTitle     human-readable title of the meeting
 * @param meetingShortCode short alphanumeric code for the meeting join URL
 * @param startTime        scheduled start time, or {@code null} for instant meetings
 * @param endTime          scheduled end time, or {@code null} for instant meetings
 * @param zoneId           host IANA time zone id
 * @param invitees         list of resolved invitees with display info
 * @param issueId          Jira issue ID, or empty when no issue is linked
 * @param issueKey         Jira issue key (e.g. {@code PROJ-123}), or empty when no issue is linked
 * @param projectKey       Jira project key, or empty when no issue is linked
 * @param occurredAt       timestamp when the event occurred
 */
public record MeetingInvitationsCreatedEvent(
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
        String issueId,
        String issueKey,
        String projectKey,
        Instant occurredAt)
        implements PublishableEvent {

    /**
     * Minimal invitee info needed by the notification service.
     *
     * @param accountId   resolved Jira account ID (always present — invitees are frontend-resolved)
     * @param email       the invite target email
     * @param displayName the user's full name at invite time
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
        return "io.github.smiskinext.meet.meeting.invitations.created.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.invitations.created";
    }

    @Override
    public String toString() {
        return "MeetingInvitationsCreatedEvent[eventId="
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
