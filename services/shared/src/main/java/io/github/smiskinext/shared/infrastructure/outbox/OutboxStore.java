package io.github.smiskinext.shared.infrastructure.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Storage port for outbox relay operations. Each service provides its own adapter implementing this
 * interface over its JPA entity/repository.
 */
public interface OutboxStore {

    void append(NewOutboxEvent event);

    List<OutboxRow> claimBatch(int batchSize);

    void markPublished(List<UUID> ids);

    void recordFailure(UUID id, String error);

    /**
     * Represents a new outbox event to be persisted within the current transaction.
     */
    record NewOutboxEvent(
            UUID eventId,
            String aggregateId,
            String aggregateType,
            String eventType,
            String topic,
            String payload,
            Instant occurredAt) {}

    /**
     * Transport-neutral representation of an unpublished outbox row.
     */
    record OutboxRow(UUID id, String tenantId, String aggregateId, String topic, String payload) {}
}
