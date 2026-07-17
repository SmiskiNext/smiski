package io.github.smiskinext.meet.infrastructure.persistence;

import io.github.smiskinext.shared.infrastructure.outbox.OutboxStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxStoreRepositoryAdapter implements OutboxStore {

    private final OutboxEventJpaRepository repository;

    public OutboxStoreRepositoryAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void append(NewOutboxEvent event) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                UUID.fromString(event.aggregateId()),
                event.aggregateType(),
                event.eventType(),
                event.topic(),
                event.payload(),
                event.occurredAt());
        repository.save(entity);
    }

    @Override
    @Transactional
    public List<OutboxRow> claimBatch(int batchSize) {
        return repository.claimBatch(batchSize).stream()
                .map(entity -> new OutboxRow(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getAggregateId().toString(),
                        entity.getTopic(),
                        entity.getPayload()))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(List<UUID> ids) {
        repository.markPublished(ids);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID id, String error) {
        repository.findByIdAcrossTenants(id).ifPresent(entity -> {
            entity.recordFailure(error);
            repository.save(entity);
        });
    }
}
