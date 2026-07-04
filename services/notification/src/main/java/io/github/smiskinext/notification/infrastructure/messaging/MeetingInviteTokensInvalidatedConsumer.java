package io.github.smiskinext.notification.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.notification.application.usecase.SendInviteUpdatedEmailUseCase;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka consumer for {@code meeting-management.meeting.invite-tokens.invalidated} events.
 *
 * <p>Each time a host changes the meeting password on a SCHEDULED meeting, all PENDING invite
 * tokens are revoked and this event is published. This consumer sends a
 * "your invite link has been updated" email to every affected invitee.
 *
 * <p>Idempotency: within a single event the same invitee email is only notified once,
 * protecting against accidental duplicate entries in the event payload.
 */
@Component
public class MeetingInviteTokensInvalidatedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(MeetingInviteTokensInvalidatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendInviteUpdatedEmailUseCase sendInviteUpdatedEmailUseCase;

    public MeetingInviteTokensInvalidatedConsumer(
            ObjectMapper objectMapper,
            SendInviteUpdatedEmailUseCase sendInviteUpdatedEmailUseCase) {
        this.objectMapper = objectMapper;
        this.sendInviteUpdatedEmailUseCase = sendInviteUpdatedEmailUseCase;
    }

    @KafkaListener(
            topics = "meeting-management.meeting.invite-tokens.invalidated",
            groupId = "#{@notificationProperties.kafka.inviteInvalidatedConsumerGroup}",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingInviteTokensInvalidated(CloudEvent cloudEvent) {
        MeetingInviteTokensInvalidatedMessage message = deserialize(cloudEvent);
        if (message == null || message.affectedInvitees().isEmpty()) {
            return;
        }
        log.info(
                "Processing invite tokens invalidated event {} for {} invitees",
                message.eventId(),
                message.affectedInvitees().size());

        Set<String> notifiedEmails = new HashSet<>();
        for (MeetingInviteTokensInvalidatedMessage.AffectedInviteeInfo invitee :
                message.affectedInvitees()) {
            if (!notifiedEmails.add(invitee.email())) {
                log.debug(
                        "Skipping duplicate notification for invitee {} in event {}",
                        invitee.email(),
                        message.eventId());
                continue;
            }
            try {
                sendInviteUpdatedEmailUseCase.send(message, invitee);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to send invite updated notification for event {} to invitee {}: {}",
                        message.eventId(),
                        invitee.email(),
                        exception.getMessage());
            }
        }
    }

    private @Nullable MeetingInviteTokensInvalidatedMessage deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received meeting-management.meeting.invite-tokens.invalidated CloudEvent"
                            + " with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(
                    cloudEvent.getData().toBytes(), MeetingInviteTokensInvalidatedMessage.class);
        } catch (Exception exception) {
            log.error(
                    "Failed to deserialize meeting-management.meeting.invite-tokens.invalidated"
                            + " CloudEvent {}: {}",
                    cloudEvent.getId(),
                    exception.getMessage());
            return null;
        }
    }
}
