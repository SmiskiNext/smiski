package io.github.smiskinext.meet.infrastructure.messaging;

import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookPublisher;
import io.github.smiskinext.meet.infrastructure.livekit.LiveKitProperties;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes verified LiveKit webhook events onto the internal Kafka topic, keyed by room name so
 * that all events for one room land on the same partition and preserve their delivery order.
 */
@Component
public class LiveKitWebhookKafkaPublisher implements LiveKitWebhookPublisher {

    private final KafkaTemplate<String, LiveKitWebhookMessage> kafkaTemplate;
    private final String topic;

    public LiveKitWebhookKafkaPublisher(
            KafkaTemplate<String, LiveKitWebhookMessage> liveKitWebhookKafkaTemplate,
            LiveKitProperties liveKitProperties) {
        this.kafkaTemplate = liveKitWebhookKafkaTemplate;
        this.topic = liveKitProperties.webhookTopic();
    }

    @Override
    public void publish(LiveKitWebhookEvent event) {
        LiveKitWebhookMessage message = new LiveKitWebhookMessage(
                event.eventType(),
                event.roomName(),
                event.roomMetadata(),
                event.participantIdentity(),
                event.participantSid(),
                event.participantAttributes(),
                event.trackSid(),
                event.trackSource(),
                event.webhookId(),
                event.occurredAt().getEpochSecond());
        String key = event.roomName() != null ? event.roomName() : event.webhookId();
        kafkaTemplate.send(topic, key, message);
    }
}
