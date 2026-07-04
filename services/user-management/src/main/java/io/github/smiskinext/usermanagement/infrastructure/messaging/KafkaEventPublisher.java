package io.github.smiskinext.usermanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.usermanagement.domain.EventPublisher;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.net.URI;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka adapter for {@link EventPublisher}. Publishes events as CloudEvents 1.0 binary content
 * mode messages using {@link KafkaTemplate}{@code <String, CloudEvent>}.
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(
            KafkaTemplate<String, CloudEvent> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PublishableEvent event) {
        byte[] data = objectMapper.writeValueAsBytes(event);
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(event.eventId().toString())
                .withType(event.eventType())
                .withSource(URI.create("user-management"))
                .withSubject(event.aggregateId().toString())
                .withTime(event.occurredAt().atOffset(ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withData("application/json", data)
                .build();

        kafkaTemplate.send(event.topic(), event.aggregateId().toString(), cloudEvent);
        log.debug(
                "Published CloudEvent to Kafka: topic={}, type={}, id={}",
                event.topic(),
                event.eventType(),
                event.eventId());
    }
}
