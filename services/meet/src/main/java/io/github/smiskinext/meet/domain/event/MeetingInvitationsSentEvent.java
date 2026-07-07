package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.meet.domain.PublishableEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a meeting is scheduled with a non-empty invitee list.
 * Carries enough information for the notification service to send invitation emails.
 *
 * <p>Each invitee has a per-invitee invite token embedded in {@code inviteeTokens}.
 * The token-based link should be used by the notification service to build the join URL.
 *
 * @param eventId          unique identifier for this event occurrence
 * @param aggregateId      ID of the meeting aggregate
 * @param meetingTitle     human-readable title of the meeting
 * @param meetingShortCode short alphanumeric code for the meeting join URL
 * @param startTime        scheduled start time, or {@code null} for open-ended meetings
 * @param invitees         list of resolved invitees with display info
 * @param inviteeTokens    map of accountId (resolved from gRPC, not the DB invitee record ID)
 *                         to raw invite token; consumers must look up tokens by accountId
 * @param occurredAt       timestamp when the event occurred
 */
public record MeetingInvitationsSentEvent(
        UUID eventId,
        UUID aggregateId,
        @Nullable String meetingTitle,
        String meetingShortCode,
        @Nullable Instant startTime,
        List<InviteeInfo> invitees,
        Map<String, String> inviteeTokens,
        Instant occurredAt)
        implements PublishableEvent {

    /**
     * Minimal invitee info needed by the notification service.
     *
     * @param accountId   resolved account ID (may be null if resolution was partial)
     * @param email       the invite target email
     * @param displayName the user's full name at invite time
     */
    public record InviteeInfo(
            @Nullable String accountId,
            String email,
            @Nullable String displayName) {}

    @Override
    public String aggregateType() {
        return "meeting";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.meeting.invitations-sent.v1";
    }

    @Override
    public String topic() {
        return "meet.meeting.invitations-sent";
    }

    @Override
    public String toString() {
        return "MeetingInvitationsSentEvent[eventId="
                + eventId
                + ", aggregateId="
                + aggregateId
                + ", meetingTitle="
                + meetingTitle
                + ", meetingShortCode="
                + meetingShortCode
                + ", startTime="
                + startTime
                + ", invitees="
                + invitees
                + ", inviteeTokenCount="
                + (inviteeTokens != null ? inviteeTokens.size() : 0)
                + ", occurredAt="
                + occurredAt
                + ']';
    }
}
