package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.MeetingInvitationsCreated;
import io.github.smiskinext.notification.application.command.SendMeetingInvitationEmailCommand;
import io.github.smiskinext.notification.application.usecase.SendMeetingInvitationEmailUseCase;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import io.github.smiskinext.notification.infrastructure.email.InvitationCalendar;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code meet.meeting.invitations.created} CloudEvents and emails each invitee a
 * {@code METHOD:REQUEST} calendar invite.
 *
 * <p>Subscribes under the fixed {@code invitation-consumer-group} so each event is emailed by
 * exactly one replica. A message that cannot be decoded into a valid invitations-created event is
 * logged and skipped so the consumer keeps running; a send failure is rethrown so the container's
 * retry and dead-letter handling engages.
 */
@Component
public class MeetingInvitationsCreatedEmailConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(MeetingInvitationsCreatedEmailConsumer.class);

    private final IcsGenerator icsGenerator;
    private final SendMeetingInvitationEmailUseCase sendMeetingInvitationEmailUseCase;

    public MeetingInvitationsCreatedEmailConsumer(
            IcsGenerator icsGenerator,
            SendMeetingInvitationEmailUseCase sendMeetingInvitationEmailUseCase) {
        this.icsGenerator = icsGenerator;
        this.sendMeetingInvitationEmailUseCase = sendMeetingInvitationEmailUseCase;
    }

    @KafkaListener(
            topics = "meet.meeting.invitations.created",
            groupId = "${app.notification.kafka.invitation-consumer-group}",
            containerFactory = "emailKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        InvitationCalendar calendar;
        List<InvitationCalendar.Attendee> attendees;
        try {
            MeetingInvitationsCreated proto = parse(event);
            attendees = toAttendees(proto);
            calendar = toCalendar(proto, attendees);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed meet.meeting.invitations.created event id={}: {}",
                    event.getId(),
                    e.getMessage());
            return;
        }

        for (InvitationCalendar.Attendee invitee : attendees) {
            String ics = icsGenerator.buildRequest(calendar);
            sendMeetingInvitationEmailUseCase.execute(
                    new SendMeetingInvitationEmailCommand(new CalendarEmail(
                            invitee.email(),
                            invitationSubject(calendar.title()),
                            invitationBody(calendar.title()),
                            ics,
                            "REQUEST",
                            "invite.ics")));
        }
    }

    private static InvitationCalendar toCalendar(
            MeetingInvitationsCreated proto, List<InvitationCalendar.Attendee> attendees) {
        return new InvitationCalendar(
                requireNonBlank(proto.getCalendarUid(), "calendarUid"),
                proto.getCalendarSequence(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                requireNonBlank(proto.getOrganizerEmail(), "organizerEmail"),
                proto.getOrganizerDisplayName(),
                attendees);
    }

    private static List<InvitationCalendar.Attendee> toAttendees(MeetingInvitationsCreated proto) {
        List<InvitationCalendar.Attendee> attendees = new ArrayList<>(proto.getInviteesCount());
        for (MeetingInvitationsCreated.InviteeInfo invitee : proto.getInviteesList()) {
            attendees.add(new InvitationCalendar.Attendee(
                    requireNonBlank(invitee.getEmail(), "invitee.email"),
                    invitee.getDisplayName(),
                    invitee.getStatus()));
        }
        if (attendees.isEmpty()) {
            throw new IllegalArgumentException("Invitations-created event carries no invitees");
        }
        return attendees;
    }

    private static String invitationSubject(@Nullable String title) {
        return "Invitation: " + (title == null ? "Meeting" : title);
    }

    private static String invitationBody(@Nullable String title) {
        return "You have been invited to " + (title == null ? "a meeting" : title)
                + ". The attached calendar invite lets you add it to your calendar.";
    }

    private static MeetingInvitationsCreated parse(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data");
        }
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        MeetingInvitationsCreated.Builder builder = MeetingInvitationsCreated.newBuilder();
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
