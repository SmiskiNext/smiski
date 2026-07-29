package io.github.smiskinext.notification.infrastructure.email;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Data needed to build a {@code METHOD:REQUEST} calendar invite.
 *
 * @param calendarUid      RFC 5545 {@code UID}
 * @param calendarSequence RFC 5545 {@code SEQUENCE}
 * @param title            meeting title used as {@code SUMMARY}
 * @param startTime        {@code DTSTART} instant, or {@code null} when unscheduled
 * @param endTime          {@code DTEND} instant, or {@code null} when unscheduled
 * @param zoneId           host IANA time zone id
 * @param organizerEmail   {@code ORGANIZER} email
 * @param organizerName    {@code ORGANIZER} display name
 * @param attendees        one entry per invitee
 */
public record InvitationCalendar(
        String calendarUid,
        int calendarSequence,
        @Nullable String title,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerName,
        List<Attendee> attendees) {

    /**
     * A single {@code ATTENDEE} line.
     *
     * @param email       attendee email
     * @param displayName attendee display name
     * @param status      participation status name (e.g. {@code NEEDS_ACTION})
     */
    public record Attendee(String email, String displayName, String status) {}
}
