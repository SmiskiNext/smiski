package io.github.smiskinext.tenant.infrastructure.messaging;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaEntity;
import io.github.smiskinext.tenant.infrastructure.persistence.OutboxEventJpaRepository;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelayScheduler(
            OutboxEventJpaRepository outboxRepository,
            KafkaTemplate<String, CloudEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<OutboxEventJpaEntity> unpublished = outboxRepository.findUnpublishedOrderByCreatedAt();
        for (OutboxEventJpaEntity row : unpublished) {
            try {
                CloudEvent cloudEvent = parseCloudEvent(row.getPayload());
                kafkaTemplate
                        .send(row.getTopic(), row.getAggregateId(), cloudEvent)
                        .get();
                row.markPublished();
                outboxRepository.save(row);
            } catch (Exception e) {
                log.warn("Failed to relay outbox event {}: {}", row.getId(), e.getMessage());
                row.recordFailure(e.getMessage());
                outboxRepository.save(row);
            }
        }
    }

    private CloudEvent parseCloudEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String dataStr = objectMapper.writeValueAsString(node.get("data"));
            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            CloudEventBuilder builder = CloudEventBuilder.v1()
                    .withId(node.get("id").asText())
                    .withType(node.get("type").asText())
                    .withSource(URI.create(node.get("source").asText()))
                    .withDataContentType(node.get("datacontenttype").asText())
                    .withData(data);

            if (node.has("dataschema") && !node.get("dataschema").isNull()) {
                builder.withDataSchema(URI.create(node.get("dataschema").asText()));
            }
            if (node.has("time") && !node.get("time").isNull()) {
                builder.withTime(OffsetDateTime.parse(node.get("time").asText()));
            }
            if (node.has("subject") && !node.get("subject").isNull()) {
                builder.withSubject(node.get("subject").asText());
            }

            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse CloudEvent from outbox payload", e);
        }
    }
}
