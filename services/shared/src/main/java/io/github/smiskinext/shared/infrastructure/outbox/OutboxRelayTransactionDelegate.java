package io.github.smiskinext.shared.infrastructure.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes the outbox relay's database operations across independent transaction boundaries.
 */
public class OutboxRelayTransactionDelegate {

    private final OutboxStore outboxStore;
    private final OutboxProperties properties;

    public OutboxRelayTransactionDelegate(OutboxStore outboxStore, OutboxProperties properties) {
        this.outboxStore = outboxStore;
        this.properties = properties;
    }

    @Transactional
    public List<OutboxStore.OutboxRow> claimBatch() {
        return outboxStore.claimBatch(properties.relay().batchSize());
    }

    @Transactional
    public void markPublished(List<UUID> ids) {
        outboxStore.markPublished(ids);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID id, String error) {
        outboxStore.recordFailure(id, error);
    }
}
