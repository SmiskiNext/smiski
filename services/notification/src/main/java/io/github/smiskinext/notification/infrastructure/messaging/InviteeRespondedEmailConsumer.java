package io.github.smiskinext.notification.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.github.smiskinext.event.meet.v1.InviteeAccepted;
import io.github.smiskinext.event.meet.v1.InviteeDeclined;
import io.github.smiskinext.event.meet.v1.InviteeTentative;
import io.github.smiskinext.notification.application.command.SendInviteeRespondedEmailCommand;
import io.github.smiskinext.notification.application.usecase.SendInviteeRespondedEmailUseCase;
import io.github.smiskinext.notification.domain.model.CalendarEmail;
import io.github.smiskinext.notification.infrastructure.email.EmailContentBuilder;
import io.github.smiskinext.notification.infrastructure.email.EmailContentBuilder.EmailContext;
import io.github.smiskinext.notification.infrastructure.email.IcsGenerator;
import io.github.smiskinext.notification.infrastructure.email.ReplyCalendar;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the invitee response events ({@code meet.invitee.accepted}, {@code meet.invitee.declined},
 * {@code meet.invitee.tentative}) and emails the organizer a {@code METHOD:REPLY} calendar carrying
 * the invitee's {@code PARTSTAT}.
 *
 * <p>Subscribes under the fixed {@code invitee-responded-consumer-group} so each response is emailed
 * by exactly one replica. A message that cannot be decoded into a valid response event is logged and
 * skipped so the consumer keeps running; a send failure is rethrown so the container's retry and
 * dead-letter handling engages.
 */
@Component
public class InviteeRespondedEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(InviteeRespondedEmailConsumer.class);

    private static final String TYPE_ACCEPTED = "io.github.smiskinext.meet.invitee.accepted.v1";
    private static final String TYPE_DECLINED = "io.github.smiskinext.meet.invitee.declined.v1";
    private static final String TYPE_TENTATIVE = "io.github.smiskinext.meet.invitee.tentative.v1";

    private record DecodedResponse(ReplyCalendar calendar, EmailContext context) {}

    private final IcsGenerator icsGenerator;
    private final EmailContentBuilder emailContentBuilder;
    private final SendInviteeRespondedEmailUseCase sendInviteeRespondedEmailUseCase;

    public InviteeRespondedEmailConsumer(
            IcsGenerator icsGenerator,
            EmailContentBuilder emailContentBuilder,
            SendInviteeRespondedEmailUseCase sendInviteeRespondedEmailUseCase) {
        this.icsGenerator = icsGenerator;
        this.emailContentBuilder = emailContentBuilder;
        this.sendInviteeRespondedEmailUseCase = sendInviteeRespondedEmailUseCase;
    }

    @KafkaListener(
            topics = {"meet.invitee.accepted", "meet.invitee.declined", "meet.invitee.tentative"},
            groupId = "${app.notification.kafka.invitee-responded-consumer-group}",
            containerFactory = "emailKafkaListenerContainerFactory")
    public void onMessage(CloudEvent event) {
        DecodedResponse decoded;
        try {
            decoded = decode(event);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping malformed invitee response event id={} type={}: {}",
                    event.getId(),
                    event.getType(),
                    e.getMessage());
            return;
        }

        String ics = icsGenerator.buildReply(decoded.calendar());
        CalendarEmail email = emailContentBuilder.buildResponse(
                decoded.calendar().organizerEmail(),
                decoded.context(),
                decoded.calendar().inviteeName(),
                decoded.calendar().responseStatus(),
                ics);
        sendInviteeRespondedEmailUseCase.execute(new SendInviteeRespondedEmailCommand(email));
    }

    private DecodedResponse decode(CloudEvent event) {
        CloudEventData data = event.getData();
        if (data == null) {
            throw new IllegalArgumentException("CloudEvent carries no data");
        }

        String type = event.getType();
        return switch (type == null ? "" : type) {
            case TYPE_ACCEPTED -> decodeAccepted(data);
            case TYPE_DECLINED -> decodeDeclined(data);
            case TYPE_TENTATIVE -> decodeTentative(data);
            default ->
                throw new IllegalArgumentException("Unsupported response event type: " + type);
        };
    }

    private DecodedResponse decodeAccepted(CloudEventData data) {
        InviteeAccepted.Builder builder = InviteeAccepted.newBuilder();
        merge(data, builder);
        InviteeAccepted proto = builder.build();
        ReplyCalendar calendar = new ReplyCalendar(
                requireNonBlank(proto.getCalendarUid(), "calendarUid"),
                proto.getCalendarSequence(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                requireNonBlank(proto.getOrganizerEmail(), "organizerEmail"),
                proto.getOrganizerDisplayName(),
                requireNonBlank(proto.getInviteeEmail(), "inviteeEmail"),
                proto.getInviteeDisplayName(),
                requireNonBlank(proto.getStatus(), "status"));
        EmailContext context = new EmailContext(
                proto.getTenantId(),
                proto.getMeetingId(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                proto.getOrganizerDisplayName(),
                proto.getShortCode(),
                blankToNull(proto.getIssueKey()));
        return new DecodedResponse(calendar, context);
    }

    private DecodedResponse decodeDeclined(CloudEventData data) {
        InviteeDeclined.Builder builder = InviteeDeclined.newBuilder();
        merge(data, builder);
        InviteeDeclined proto = builder.build();
        ReplyCalendar calendar = new ReplyCalendar(
                requireNonBlank(proto.getCalendarUid(), "calendarUid"),
                proto.getCalendarSequence(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                requireNonBlank(proto.getOrganizerEmail(), "organizerEmail"),
                proto.getOrganizerDisplayName(),
                requireNonBlank(proto.getInviteeEmail(), "inviteeEmail"),
                proto.getInviteeDisplayName(),
                requireNonBlank(proto.getStatus(), "status"));
        EmailContext context = new EmailContext(
                proto.getTenantId(),
                proto.getMeetingId(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                proto.getOrganizerDisplayName(),
                proto.getShortCode(),
                blankToNull(proto.getIssueKey()));
        return new DecodedResponse(calendar, context);
    }

    private DecodedResponse decodeTentative(CloudEventData data) {
        InviteeTentative.Builder builder = InviteeTentative.newBuilder();
        merge(data, builder);
        InviteeTentative proto = builder.build();
        ReplyCalendar calendar = new ReplyCalendar(
                requireNonBlank(proto.getCalendarUid(), "calendarUid"),
                proto.getCalendarSequence(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                requireNonBlank(proto.getOrganizerEmail(), "organizerEmail"),
                proto.getOrganizerDisplayName(),
                requireNonBlank(proto.getInviteeEmail(), "inviteeEmail"),
                proto.getInviteeDisplayName(),
                requireNonBlank(proto.getStatus(), "status"));
        EmailContext context = new EmailContext(
                proto.getTenantId(),
                proto.getMeetingId(),
                blankToNull(proto.getMeetingTitle()),
                parseInstant(proto.getStartTime()),
                parseInstant(proto.getEndTime()),
                proto.getZoneId(),
                proto.getOrganizerDisplayName(),
                proto.getShortCode(),
                blankToNull(proto.getIssueKey()));
        return new DecodedResponse(calendar, context);
    }

    private static void merge(CloudEventData data, Message.Builder builder) {
        String json = new String(data.toBytes(), StandardCharsets.UTF_8);
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Malformed proto-JSON data: " + e.getMessage(), e);
        }
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
