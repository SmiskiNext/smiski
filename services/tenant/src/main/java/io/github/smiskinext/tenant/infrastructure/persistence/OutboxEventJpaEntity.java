package io.github.smiskinext.tenant.infrastructure.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 255, updatable = false)
    private String tenantId;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private @Nullable Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private @Nullable String lastError;

    protected OutboxEventJpaEntity() {}

    public OutboxEventJpaEntity(
            String tenantId,
            String aggregateId,
            String aggregateType,
            String eventType,
            String topic,
            String payload,
            Instant createdAt) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
        this.retryCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getPublishedAt() {
        return publishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public @Nullable String getLastError() {
        return lastError;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error;
    }
}
