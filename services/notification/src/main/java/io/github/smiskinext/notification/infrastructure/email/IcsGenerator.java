package io.github.smiskinext.notification.infrastructure.email;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.parameter.ParticipationStatus;
import biweekly.property.Attendee;
import biweekly.property.Method;
import biweekly.property.Organizer;
import java.time.Instant;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Generates RFC 5545 iCalendar objects for meeting invitations and replies using biweekly.
 *
 * <p>REQUEST calendars carry one {@code ATTENDEE} per invitee; REPLY calendars carry the single
 * responding attendee with a {@code PARTSTAT} mapped from the meet invitee status: accepted to
 * {@code ACCEPTED}, declined to {@code DECLINED}, tentative to {@code TENTATIVE}.
 */
@Component
public class IcsGenerator {

    private static final String METHOD_REQUEST = "REQUEST";
    private static final String METHOD_REPLY = "REPLY";

    public String buildRequest(InvitationCalendar calendar) {
        ICalendar ical = new ICalendar();
        ical.setMethod(new Method(METHOD_REQUEST));

        VEvent event = baseEvent(
                calendar.calendarUid(),
                calendar.calendarSequence(),
                calendar.title(),
                calendar.startTime(),
                calendar.endTime(),
                calendar.organizerEmail(),
                calendar.organizerName());

        for (InvitationCalendar.Attendee invitee : calendar.attendees()) {
            Attendee attendee = new Attendee(invitee.displayName(), invitee.email());
            attendee.setParticipationStatus(mapPartStat(invitee.status()));
            attendee.setRsvp(true);
            event.addAttendee(attendee);
        }

        ical.addEvent(event);
        return Biweekly.write(ical).go();
    }

    public String buildReply(ReplyCalendar calendar) {
        ICalendar ical = new ICalendar();
        ical.setMethod(new Method(METHOD_REPLY));

        VEvent event = baseEvent(
                calendar.calendarUid(),
                calendar.calendarSequence(),
                calendar.title(),
                calendar.startTime(),
                calendar.endTime(),
                calendar.organizerEmail(),
                calendar.organizerName());

        Attendee attendee = new Attendee(calendar.inviteeName(), calendar.inviteeEmail());
        attendee.setParticipationStatus(mapPartStat(calendar.responseStatus()));
        event.addAttendee(attendee);

        ical.addEvent(event);
        return Biweekly.write(ical).go();
    }

    private static VEvent baseEvent(
            String calendarUid,
            int calendarSequence,
            @Nullable String title,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            String organizerEmail,
            String organizerName) {
        VEvent event = new VEvent();
        event.setUid(calendarUid);
        event.setSequence(calendarSequence);
        if (title != null) {
            event.setSummary(title);
        }
        if (startTime != null) {
            event.setDateStart(Date.from(startTime));
        }
        if (endTime != null) {
            event.setDateEnd(Date.from(endTime));
        }
        event.setOrganizer(new Organizer(organizerName, organizerEmail));
        return event;
    }

    private static ParticipationStatus mapPartStat(String status) {
        return switch (status) {
            case "ACCEPTED" -> ParticipationStatus.ACCEPTED;
            case "DECLINED" -> ParticipationStatus.DECLINED;
            case "TENTATIVE" -> ParticipationStatus.TENTATIVE;
            default -> ParticipationStatus.NEEDS_ACTION;
        };
    }
}
