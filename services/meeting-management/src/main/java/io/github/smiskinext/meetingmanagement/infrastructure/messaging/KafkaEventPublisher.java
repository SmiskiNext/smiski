package io.github.smiskinext.meetingmanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;
import io.github.smiskinext.meetingmanagement.domain.port.EventPublisher;
import java.net.URI;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka adapter for {@link EventPublisher}. Publishes events as CloudEvents 1.0 binary content
 * mode messages.
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
                .withSource(URI.create("meeting-management"))
                .withSubject(event.aggregateId().toString())
                .withTime(event.occurredAt().atOffset(ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withData("application/json", data)
                .build();

        kafkaTemplate.send(event.topic(), event.aggregateId().toString(), cloudEvent);
        log.debug(
                "Published CloudEvent: topic={}, type={}, id={}",
                event.topic(),
                event.eventType(),
                event.eventId());
    }
}
