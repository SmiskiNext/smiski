package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.MeetingInfoSnapshot;
import io.github.smiskinext.event.meet.v1.MeetingInfoUpdated;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.domain.port.EmailSender;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import io.github.smiskinext.notification.infrastructure.email.InvitationCalendar;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code meet.meeting.info.updated} CloudEvents and emails each invitee an updated
 * {@code METHOD:REQUEST} calendar invite when the meeting's scheduled time changes.
 *
 * <p>Only time-related field changes (start time, end time, zone id) trigger an email. Title,
 * description, or issue-link-only changes are silently skipped. If there are no invitees on the
 * event, no email is sent regardless of whether the time changed.
 *
 * <p>Subscribes under the fixed {@code meeting-info-updated-consumer-group} so each event is
 * emailed by exactly one replica. A message that cannot be decoded is logged and skipped so the
 * consumer keeps running; a send failure is rethrown so the container's retry and dead-letter
 * handling engages.
 */
@Component
public class MeetingInfoUpdatedEmailConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(MeetingInfoUpdatedEmailConsumer.class);

    private final IcsGenerator icsGenerator;
    private final EmailSender emailSender;

    public MeetingInfoUpdatedEmailConsumer(IcsGenerator icsGenerator, EmailSender emailSender) {
        this.icsGenerator = icsGenerator;
        this.emailSender = emailSender;
    }

    @KafkaListener(
            topics = "meet.meeting.info.updated",
            groupId = "${app.notification.kafka.meeting-info-updated-consumer-group}",
            containerFactory = "emailKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        MeetingInfoUpdated proto;
        try {
            proto = parse(event);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed meet.meeting.info.updated event id={}: {}",
                    event.getId(),
                    e.getMessage());
            return;
        }

        if (!timeFieldsChanged(proto.getOldInfo(), proto.getNewInfo())) {
            return;
        }

        if (proto.getInviteesCount() == 0) {
            return;
        }

        List<InvitationCalendar.Attendee> attendees = toAttendees(proto);
        InvitationCalendar calendar = toCalendar(proto.getNewInfo(), attendees);

        for (InvitationCalendar.Attendee invitee : attendees) {
            String ics = icsGenerator.buildRequest(calendar);
            emailSender.send(new CalendarEmail(
                    invitee.email(),
                    updateSubject(calendar.title()),
                    updateBody(calendar.title()),
                    ics,
                    "REQUEST",
                    "invite.ics"));
        }
    }

    private static boolean timeFieldsChanged(
            MeetingInfoSnapshot oldInfo, MeetingInfoSnapshot newInfo) {
        return !Objects.equals(oldInfo.getStartTime(), newInfo.getStartTime())
                || !Objects.equals(oldInfo.getEndTime(), newInfo.getEndTime())
                || !Objects.equals(oldInfo.getZoneId(), newInfo.getZoneId());
    }

    private static InvitationCalendar toCalendar(
            MeetingInfoSnapshot newInfo, List<InvitationCalendar.Attendee> attendees) {
        return new InvitationCalendar(
                requireNonBlank(newInfo.getCalendarUid(), "calendarUid"),
                newInfo.getCalendarSequence(),
                blankToNull(newInfo.getTitle()),
                parseInstant(newInfo.getStartTime()),
                parseInstant(newInfo.getEndTime()),
                newInfo.getZoneId(),
                requireNonBlank(newInfo.getOrganizerEmail(), "organizerEmail"),
                newInfo.getOrganizerDisplayName(),
                attendees);
    }

    private static List<InvitationCalendar.Attendee> toAttendees(MeetingInfoUpdated proto) {
        List<InvitationCalendar.Attendee> attendees = new ArrayList<>(proto.getInviteesCount());
        for (MeetingInfoUpdated.InviteeInfo invitee : proto.getInviteesList()) {
            attendees.add(new InvitationCalendar.Attendee(
                    requireNonBlank(invitee.getEmail(), "invitee.email"),
                    invitee.getDisplayName(),
                    invitee.getStatus()));
        }
        return attendees;
    }

    private static String updateSubject(@Nullable String title) {
        return "Updated: " + (title == null ? "Meeting" : title);
    }

    private static String updateBody(@Nullable String title) {
        return "The meeting " + (title == null ? "" : title + " ")
                + "has been rescheduled. The attached calendar invite updates your calendar.";
    }

    private static MeetingInfoUpdated parse(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data");
        }
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        MeetingInfoUpdated.Builder builder = MeetingInfoUpdated.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Malformed proto-JSON data: " + e.getMessage(), e);
        }
        return builder.build();
    }

    private static @Nullable Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static @Nullable String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
