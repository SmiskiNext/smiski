package io.github.smiskinext.shared.infrastructure.outbox;

import io.cloudevents.CloudEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbox relay with three transaction boundaries:
 *
 * <ol>
 *   <li>Short read transaction: claim a bounded batch via {@link OutboxRelayTransactionDelegate}
 *   <li>Transaction-free publish: fire async transport sends and await futures
 *   <li>Short write transactions: mark published rows, record failures independently
 * </ol>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRelayTransactionDelegate transactionDelegate;
    private final OutboxTransport transport;
    private final CloudEventEncoder cloudEventEncoder;

    public OutboxRelay(
            OutboxRelayTransactionDelegate transactionDelegate,
            OutboxTransport transport,
            CloudEventEncoder cloudEventEncoder) {
        this.transactionDelegate = transactionDelegate;
        this.transport = transport;
        this.cloudEventEncoder = cloudEventEncoder;
    }

    public void relay() {
        List<OutboxStore.OutboxRow> batch = transactionDelegate.claimBatch();
        if (batch.isEmpty()) {
            return;
        }

        List<UUID> succeeded = new ArrayList<>();
        List<FailedRow> failed = new ArrayList<>();

        publishBatch(batch, succeeded, failed);

        if (!succeeded.isEmpty()) {
            transactionDelegate.markPublished(succeeded);
        }
        for (FailedRow failedRow : failed) {
            transactionDelegate.recordFailure(failedRow.id(), failedRow.error());
        }
    }

    private void publishBatch(
            List<OutboxStore.OutboxRow> batch, List<UUID> succeeded, List<FailedRow> failed) {
        List<PendingSend> pending = new ArrayList<>(batch.size());

        for (OutboxStore.OutboxRow row : batch) {
            try {
                CloudEvent cloudEvent = cloudEventEncoder.decode(row.payload());
                CompletableFuture<Void> future =
                        transport.send(row.topic(), row.aggregateId(), cloudEvent);
                pending.add(new PendingSend(row.id(), future));
            } catch (Exception e) {
                log.warn("Failed to decode outbox row {}: {}", row.id(), e.getMessage());
                failed.add(new FailedRow(row.id(), e.getMessage()));
            }
        }

        for (PendingSend send : pending) {
            try {
                send.future().join();
                succeeded.add(send.id());
            } catch (Exception e) {
                String errorMessage =
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                log.warn("Failed to publish outbox row {}: {}", send.id(), errorMessage);
                failed.add(new FailedRow(send.id(), errorMessage));
            }
        }
    }

    private record PendingSend(UUID id, CompletableFuture<Void> future) {}

    private record FailedRow(UUID id, String error) {}
}
