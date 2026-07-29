package io.github.smiskinext.meet.domain.event;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when an invitee declines a meeting invitation.
 *
 * <p>Carries the meeting context required to build an iCalendar reply without a follow-up lookup:
 * meeting title, time range, timezone, organizer identity, invitee display name, and the calendar
 * UID/SEQUENCE aligned with RFC 5545.
 */
public record InviteeDeclinedEvent(
        UUID eventId,
        String tenantId,
        UUID meetingId,
        String inviterId,
        UUID inviteeId,
        String inviteeEmail,
        String status,
        Instant declinedAt,
        @Nullable String meetingTitle,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String inviteeDisplayName,
        String calendarUid,
        int calendarSequence)
        implements PublishableEvent {

    @Override
    public String aggregateId() {
        return meetingId.toString();
    }

    @Override
    public String aggregateType() {
        return "meeting-invitee";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.meet.invitee.declined.v1";
    }

    @Override
    public String topic() {
        return "meet.invitee.declined";
    }

    @Override
    public Instant occurredAt() {
        return declinedAt;
    }
}
