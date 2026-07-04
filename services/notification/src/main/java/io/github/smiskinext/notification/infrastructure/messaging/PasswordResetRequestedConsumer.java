package io.github.smiskinext.notification.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.github.smiskinext.notification.application.usecase.SendPasswordResetEmailUseCase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka consumer for password reset requested events.
 * Triggers sending password reset OTP emails.
 */
@Component
public class PasswordResetRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendPasswordResetEmailUseCase sendPasswordResetEmailUseCase;

    public PasswordResetRequestedConsumer(
            ObjectMapper objectMapper,
            SendPasswordResetEmailUseCase sendPasswordResetEmailUseCase) {
        this.objectMapper = objectMapper;
        this.sendPasswordResetEmailUseCase = sendPasswordResetEmailUseCase;
    }

    @KafkaListener(
            topics = "user-management.password-reset.requested",
            groupId = "#{@notificationProperties.kafka.invitationConsumerGroup}",
            containerFactory = "cloudEventKafkaListenerContainerFactory")
    public void onPasswordResetRequested(CloudEvent cloudEvent) {
        PasswordResetRequestedMessage message = deserialize(cloudEvent);
        if (message == null) {
            return;
        }
        log.info(
                "Processing password reset request {} for email {}",
                message.eventId(),
                maskEmail(message.email()));
        try {
            sendPasswordResetEmailUseCase.send(message);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to send password reset email for event {}: {}",
                    message.eventId(),
                    exception.getMessage());
        }
    }

    private @Nullable PasswordResetRequestedMessage deserialize(CloudEvent cloudEvent) {
        if (cloudEvent.getData() == null) {
            log.warn(
                    "Received user-management.password-reset.requested CloudEvent with no data payload: {}",
                    cloudEvent.getId());
            return null;
        }
        try {
            return objectMapper.readValue(
                    cloudEvent.getData().toBytes(), PasswordResetRequestedMessage.class);
        } catch (Exception exception) {
            log.error(
                    "Failed to deserialize user-management.password-reset.requested CloudEvent {}: {}",
                    cloudEvent.getId(),
                    exception.getMessage());
            return null;
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
