package io.github.smiskinext.meet.domain.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of the meeting context an invitee response needs to build an iCalendar reply.
 *
 * <p>Supplied by the application layer from the loaded {@link Meeting} aggregate and embedded into
 * the enriched invitee-response events so the notification service can render a reply without a
 * follow-up lookup. Aligned with the RFC 5545 calendar properties: {@code calendarUid} maps to
 * {@code UID} and {@code calendarSequence} to {@code SEQUENCE}.
 */
public record MeetingContext(
        @Nullable String meetingTitle,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        String zoneId,
        String organizerEmail,
        String organizerDisplayName,
        String calendarUid,
        int calendarSequence) {}
