package io.github.smiskinext.shared.infrastructure.outbox;

import io.github.smiskinext.shared.domain.PublishableEvent;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link PublishableEvent} instances dispatched through the application-event bus and
 * enqueues them into the transactional outbox via {@link OutboxEventPublisher}. Runs at
 * {@link TransactionPhase#BEFORE_COMMIT} to ensure the outbox INSERT is atomic with the aggregate
 * state change.
 */
public class OutboxDomainEventListener {

    private final OutboxEventPublisher outboxEventPublisher;

    public OutboxDomainEventListener(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPublishableEvent(PublishableEvent event) {
        outboxEventPublisher.publish(event);
    }
}
