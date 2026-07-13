package io.github.smiskinext.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A {@link DomainEvent} intended for external publishing via the transactional outbox (CloudEvents
 * 1.0). Services must implement this interface for events that should be relayed to external
 * consumers.
 */
public interface PublishableEvent extends DomainEvent {

    UUID eventId();

    String aggregateId();

    String aggregateType();

    String eventType();

    String topic();

    @Override
    Instant occurredAt();
}
