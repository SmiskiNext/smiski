package io.github.smiskinext.meet.infrastructure.messaging;

import io.github.smiskinext.meet.application.usecase.HandleLiveKitWebhookUseCase;
import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes verified LiveKit webhook events from the internal topic and drives the processing use
 * case. The consumer group is shared across meet instances so delivery is load-balanced; per-room
 * ordering is preserved by the room-name partition key set at publish time.
 */
@Component
public class LiveKitWebhookConsumer {

    private final HandleLiveKitWebhookUseCase handleLiveKitWebhookUseCase;

    public LiveKitWebhookConsumer(HandleLiveKitWebhookUseCase handleLiveKitWebhookUseCase) {
        this.handleLiveKitWebhookUseCase = handleLiveKitWebhookUseCase;
    }

    @KafkaListener(
            topics = "${app.livekit.webhook-topic}",
            groupId = "${app.livekit.webhook-consumer-group}",
            containerFactory = "liveKitWebhookKafkaListenerContainerFactory")
    public void onMessage(LiveKitWebhookMessage message) {
        handleLiveKitWebhookUseCase.handle(toEvent(message));
    }

    private LiveKitWebhookEvent toEvent(LiveKitWebhookMessage message) {
        return new LiveKitWebhookEvent(
                message.eventType(),
                message.roomName(),
                message.roomMetadata(),
                message.participantIdentity(),
                message.participantSid(),
                message.participantAttributes(),
                message.trackSid(),
                message.trackSource(),
                message.webhookId(),
                Instant.ofEpochSecond(message.occurredAtEpochSecond()));
    }
}
