package io.github.smiskinext.notification.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.notification.application.usecase.SendInviteeRespondedEmailUseCase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class InviteeRespondedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InviteeRespondedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendInviteeRespondedEmailUseCase sendInviteeRespondedEmailUseCase;

    public InviteeRespondedConsumer(
            ObjectMapper objectMapper,
            SendInviteeRespondedEmailUseCase sendInviteeRespondedEmailUseCase) {
        this.objectMapper = objectMapper;
        this.sendInviteeRespondedEmailUseCase = sendInviteeRespondedEmailUseCase;
    }

    @KafkaListener(
            topics = {"meeting-management.invitee.accepted", "meeting-management.invitee.declined"},
            groupId = "#{@notificationProperties.kafka.inviteeRespondedConsumerGroup}",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onInviteeResponded(CloudEvent cloudEvent) {
        InviteeRespondedMessage message = deserialize(cloudEvent);
        if (message == null) {
            return;
        }
        log.info(
                "Processing invitee response event {} for invitee {}",
                message.eventId(),
                message.resolvedInviteeId());
        try {
            sendInviteeRespondedEmailUseCase.send(message);
        } catch (RuntimeException exception) {
            log.error("Failed to process invitee response event {}", message.eventId(), exception);
        }
    }

    private @Nullable InviteeRespondedMessage deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received invitee response CloudEvent with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(
                    cloudEvent.getData().toBytes(), InviteeRespondedMessage.class);
        } catch (Exception exception) {
            log.error(
                    "Failed to deserialize invitee response CloudEvent {}: {}",
                    cloudEvent.getId(),
                    exception.getMessage());
            return null;
        }
    }
}
