package io.github.smiskinext.notification.infrastructure.email;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Data needed to build a {@code METHOD:REPLY} calendar response from a single invitee.
 *
 * @param calendarUid       RFC 5545 {@code UID}
 * @param calendarSequence  RFC 5545 {@code SEQUENCE}
 * @param title             meeting title used as {@code SUMMARY}
 * @param startTime         {@code DTSTART} instant, or {@code null} when unscheduled
 * @param endTime           {@code DTEND} instant, or {@code null} when unscheduled
 * @param zoneId            host IANA time zone id
 * @param organizerEmail    {@code ORGANIZER} email
 * @param organizerName     {@code ORGANIZER} display name
 * @param inviteeEmail      responding attendee email
 * @param inviteeName       responding attendee display name
 * @param responseStatus    the invitee status name mapped to {@code PARTSTAT}
 */
public record ReplyCalendar(
        String calendarUid,
        int calendarSequence,
        @Nullable String title,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerName,
        String inviteeEmail,
        String inviteeName,
        String responseStatus) {}
