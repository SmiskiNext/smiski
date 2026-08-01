package io.github.smiskinext.notification.infrastructure.email;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Attendee;
import biweekly.property.Method;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.event.meet.v1.InviteeEmailReplyReceived;
import io.github.smiskinext.notification.domain.port.InboundEmailReplyProcessor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Processes inbound email webhooks from Resend.
 *
 * <p>Parses the webhook payload to extract the email ID, fetches the text/calendar attachment
 * from the Resend API, parses the iMIP METHOD:REPLY to extract the calendar UID, attendee email,
 * and PARTSTAT, then publishes a {@code meet.invitee.email-reply.received} CloudEvent.
 *
 * <p>Any failure in extraction (missing attachment, unparseable iCalendar, unknown PARTSTAT) is
 * logged and skipped — no event is published and the endpoint stays available.
 */
@Component
public class InboundEmailReplyProcessorAdapter implements InboundEmailReplyProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(InboundEmailReplyProcessorAdapter.class);
    private static final String TOPIC = "meet.invitee.email-reply.received";
    private static final String EVENT_TYPE =
            "io.github.smiskinext.meet.invitee.email-reply.received.v1";
    private static final String EVENT_SOURCE = "notification-service";

    private final ResendReceivedEmailClient receivedEmailClient;
    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InboundEmailReplyProcessorAdapter(
            ResendReceivedEmailClient receivedEmailClient,
            KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        this.receivedEmailClient = receivedEmailClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public void processInboundEmail(String webhookPayload) {
        String emailId = extractEmailId(webhookPayload);
        if (emailId == null) {
            log.debug("Inbound webhook skipped: could not extract email id from payload");
            return;
        }

        String calendarContent = receivedEmailClient.fetchCalendarAttachment(emailId);
        if (calendarContent == null) {
            log.debug("Inbound email {} skipped: no text/calendar attachment found", emailId);
            return;
        }

        ParsedReply reply = parseReply(calendarContent);
        if (reply == null) {
            log.debug("Inbound email  skipped: could not parse iMIP reply", emailId);
            return;
        }

        publishEvent(reply);
    }

    private @Nullable String extractEmailId(String webhookPayload) {
        try {
            JsonNode root = objectMapper.readTree(webhookPayload);
            JsonNode data = root.path("data");
            JsonNode emailIdNode = data.path("email_id");
            if (emailIdNode.isMissingNode() || emailIdNode.isNull()) {
                return null;
            }
            String value = emailIdNode.asText();
            return value.isBlank() ? null : value;
        } catch (Exception e) {
            log.debug("Failed to parse webhook payload for email id: {}", e.getMessage());
            return null;
        }
    }

    private @Nullable ParsedReply parseReply(String calendarContent) {
        try {
            ICalendar ical = Biweekly.parse(calendarContent).first();
            if (ical == null) {
                return null;
            }

            Method method = ical.getMethod();
            if (method == null || !"REPLY".equalsIgnoreCase(method.getValue())) {
                return null;
            }

            VEvent event = ical.getEvents().isEmpty() ? null : ical.getEvents().getFirst();
            if (event == null || event.getUid() == null) {
                return null;
            }

            String uid = event.getUid().getValue();
            if (uid == null || uid.isBlank()) {
                return null;
            }

            if (event.getAttendees().isEmpty()) {
                return null;
            }

            Attendee attendee = event.getAttendees().getFirst();
            String attendeeEmail = attendee.getEmail();
            if (attendeeEmail == null || attendeeEmail.isBlank()) {
                return null;
            }

            var partStat = attendee.getParticipationStatus();
            if (partStat == null) {
                return null;
            }

            String status = mapPartStat(partStat.getValue());
            if (status == null) {
                log.debug("Skipping unrecognized PARTSTAT: {}", partStat.getValue());
                return null;
            }

            return new ParsedReply(uid, attendeeEmail.toLowerCase().strip(), status);
        } catch (Exception e) {
            log.debug("Failed to parse iCalendar reply: {}", e.getMessage());
            return null;
        }
    }

    private @Nullable String mapPartStat(String partStat) {
        if (partStat == null) return null;
        return switch (partStat.toUpperCase()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "DECLINED";
            case "TENTATIVE" -> "TENTATIVE";
            default -> null;
        };
    }

    private void publishEvent(ParsedReply reply) {
        InviteeEmailReplyReceived.Builder proto = InviteeEmailReplyReceived.newBuilder()
                .setCalendarUid(reply.calendarUid())
                .setInviteeEmail(reply.inviteeEmail())
                .setStatus(reply.status());

        String protoJson;
        try {
            protoJson = JsonFormat.printer().omittingInsignificantWhitespace().print(proto);
        } catch (Exception e) {
            log.error("Failed to serialize email-reply event to proto-JSON", e);
            return;
        }

        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(EVENT_TYPE)
                .withSource(URI.create(EVENT_SOURCE))
                .withTime(OffsetDateTime.now())
                .withData("application/json", protoJson.getBytes(StandardCharsets.UTF_8))
                .build();

        kafkaTemplate.send(TOPIC, reply.calendarUid(), cloudEvent);
    }

    private record ParsedReply(String calendarUid, String inviteeEmail, String status) {}
}
