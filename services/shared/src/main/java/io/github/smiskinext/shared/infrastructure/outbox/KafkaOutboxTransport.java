package io.github.smiskinext.shared.infrastructure.outbox;

import io.cloudevents.CloudEvent;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaOutboxTransport implements OutboxTransport {

    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;

    public KafkaOutboxTransport(KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public CompletableFuture<Void> send(String topic, String key, CloudEvent event) {
        return kafkaTemplate.send(topic, key, event).thenApply(result -> null);
    }
}
