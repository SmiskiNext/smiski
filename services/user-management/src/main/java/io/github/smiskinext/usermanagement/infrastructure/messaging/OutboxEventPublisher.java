package io.github.smiskinext.usermanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled poller that reads unpublished rows from {@code outbox_event} and publishes them to
 * Kafka. Implements at-least-once delivery with DLT routing after 3 failures.
 */
@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final int MAX_RETRIES = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventJpaEntity> pending = outboxEventRepository.findAllByPublishedAtIsNull();
        for (OutboxEventJpaEntity row : pending) {
            if (row.getRetryCount() >= MAX_RETRIES) {
                publishToDlt(row);
            } else {
                tryPublish(row);
            }
        }
    }

    private void tryPublish(OutboxEventJpaEntity row) {
        try {
            byte[] data = row.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withType(row.getEventType())
                    .withSource(URI.create("user-management"))
                    .withSubject(row.getAggregateId().toString())
                    .withTime(row.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                    .withDataContentType("application/json")
                    .withData("application/json", data)
                    .build();

            kafkaTemplate
                    .send(row.getTopic(), row.getAggregateId().toString(), cloudEvent)
                    .get();

            row.setPublishedAt(Instant.now());
            outboxEventRepository.save(row);
            log.debug("Outbox row published: id={}, topic={}", row.getId(), row.getTopic());
        } catch (Exception e) {
            row.incrementRetryCount();
            row.setLastError(truncate(e.getMessage(), 1000));
            outboxEventRepository.save(row);
            log.warn(
                    "Failed to publish outbox row id={}, retry={}: {}",
                    row.getId(),
                    row.getRetryCount(),
                    e.getMessage());
        }
    }

    private void publishToDlt(OutboxEventJpaEntity row) {
        String dltTopic = row.getTopic() + "-dlt";
        try {
            byte[] data = row.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withType(row.getEventType())
                    .withSource(URI.create("user-management"))
                    .withSubject(row.getAggregateId().toString())
                    .withTime(row.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                    .withDataContentType("application/json")
                    .withData("application/json", data)
                    .build();

            kafkaTemplate
                    .send(dltTopic, row.getAggregateId().toString(), cloudEvent)
                    .get();

            row.setPublishedAt(Instant.now());
            outboxEventRepository.save(row);
            log.warn(
                    "Outbox row moved to DLT after {} retries: id={}, dltTopic={}",
                    MAX_RETRIES,
                    row.getId(),
                    dltTopic);
        } catch (Exception e) {
            log.error("Failed to publish to DLT: id={}, dltTopic={}", row.getId(), dltTopic, e);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
