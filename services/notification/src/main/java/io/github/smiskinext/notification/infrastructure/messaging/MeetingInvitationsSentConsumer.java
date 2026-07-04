package io.github.smiskinext.notification.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.notification.application.usecase.SendMeetingInvitationEmailUseCase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MeetingInvitationsSentConsumer {

    private static final Logger log = LoggerFactory.getLogger(MeetingInvitationsSentConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendMeetingInvitationEmailUseCase sendMeetingInvitationEmailUseCase;

    public MeetingInvitationsSentConsumer(
            ObjectMapper objectMapper,
            SendMeetingInvitationEmailUseCase sendMeetingInvitationEmailUseCase) {
        this.objectMapper = objectMapper;
        this.sendMeetingInvitationEmailUseCase = sendMeetingInvitationEmailUseCase;
    }

    @KafkaListener(
            topics = "meeting-management.meeting.invitations.sent",
            groupId = "#{@notificationProperties.kafka.invitationConsumerGroup}",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingInvitationsSent(CloudEvent cloudEvent) {
        MeetingInvitationsSentMessage message = deserialize(cloudEvent);
        if (message == null || message.invitees().isEmpty()) {
            return;
        }
        log.info(
                "Processing invitation event {} for {} invitees",
                message.eventId(),
                message.invitees().size());
        for (MeetingInvitationsSentMessage.InviteeInfo invitee : message.invitees()) {
            try {
                sendMeetingInvitationEmailUseCase.send(message, invitee);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to send invitation event {} to invitee {}: {}",
                        message.eventId(),
                        invitee.email(),
                        exception.getMessage());
            }
        }
    }

    private @Nullable MeetingInvitationsSentMessage deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received meeting-management.meeting.invitations.sent CloudEvent with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(
                    cloudEvent.getData().toBytes(), MeetingInvitationsSentMessage.class);
        } catch (Exception exception) {
            log.error(
                    "Failed to deserialize meeting-management.meeting.invitations.sent CloudEvent {}: {}",
                    cloudEvent.getId(),
                    exception.getMessage());
            return null;
        }
    }
}
