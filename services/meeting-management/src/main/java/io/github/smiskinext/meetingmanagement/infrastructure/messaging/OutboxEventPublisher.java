package io.github.smiskinext.meetingmanagement.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.meetingmanagement.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.meetingmanagement.infrastructure.persistence.OutboxEventJpaRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
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

    private final OutboxEventJpaRepository outboxEventRepository;
    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;

    public OutboxEventPublisher(
            OutboxEventJpaRepository outboxEventRepository,
            KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventJpaEntity> pending = outboxEventRepository.findAllUnpublished();
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
            CloudEvent cloudEvent = buildCloudEvent(row, "meeting-management");
            kafkaTemplate
                    .send(row.getTopic(), row.getAggregateId().toString(), cloudEvent)
                    .get();
            row.markPublished();
            outboxEventRepository.save(row);
            log.debug("Outbox row published: id={}, topic={}", row.getId(), row.getTopic());
        } catch (Exception e) {
            row.recordFailure(truncate(e.getMessage(), 1000));
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
            CloudEvent cloudEvent = buildCloudEvent(row, "meeting-management");
            kafkaTemplate
                    .send(dltTopic, row.getAggregateId().toString(), cloudEvent)
                    .get();
            row.markPublished();
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

    private static CloudEvent buildCloudEvent(OutboxEventJpaEntity row, String source) {
        byte[] data = row.getPayload().getBytes(StandardCharsets.UTF_8);
        return CloudEventBuilder.v1()
                .withId(row.getId().toString())
                .withType(row.getEventType())
                .withSource(URI.create(source))
                .withSubject(row.getAggregateId().toString())
                .withTime(row.getCreatedAt().atOffset(ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withData("application/json", data)
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
