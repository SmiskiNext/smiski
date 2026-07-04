package io.github.smiskinext.notification.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.notification.application.usecase.SendMeetingCancelledEmailUseCase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MeetingCancelledNotificationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(MeetingCancelledNotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendMeetingCancelledEmailUseCase sendMeetingCancelledEmailUseCase;

    public MeetingCancelledNotificationConsumer(
            ObjectMapper objectMapper,
            SendMeetingCancelledEmailUseCase sendMeetingCancelledEmailUseCase) {
        this.objectMapper = objectMapper;
        this.sendMeetingCancelledEmailUseCase = sendMeetingCancelledEmailUseCase;
    }

    @KafkaListener(
            topics = "meeting-management.meeting.cancelled",
            groupId = "#{@notificationProperties.kafka.invitationConsumerGroup}",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onMeetingCancelled(CloudEvent cloudEvent) {
        MeetingCancelledMessage message = deserialize(cloudEvent);
        if (message == null || message.invitees().isEmpty()) {
            return;
        }
        log.info(
                "Processing cancelled meeting event {} for {} invitees",
                message.eventId(),
                message.invitees().size());
        for (MeetingCancelledMessage.InviteeInfo invitee : message.invitees()) {
            if (!isNotifiableStatus(invitee.status())) {
                log.debug(
                        "Skipping cancellation event {} for invitee {} with status {}",
                        message.eventId(),
                        invitee.email(),
                        invitee.status());
                continue;
            }
            try {
                sendMeetingCancelledEmailUseCase.send(message, invitee);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to send cancellation event {} to invitee {}: {}",
                        message.eventId(),
                        invitee.email(),
                        exception.getMessage());
            }
        }
    }

    private @Nullable MeetingCancelledMessage deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received meeting-management.meeting.cancelled CloudEvent with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(
                    cloudEvent.getData().toBytes(), MeetingCancelledMessage.class);
        } catch (Exception exception) {
            log.error(
                    "Failed to deserialize meeting-management.meeting.cancelled CloudEvent {}: {}",
                    cloudEvent.getId(),
                    exception.getMessage());
            return null;
        }
    }

    private boolean isNotifiableStatus(String status) {
        return "PENDING".equals(status) || "ACCEPTED".equals(status);
    }
}
