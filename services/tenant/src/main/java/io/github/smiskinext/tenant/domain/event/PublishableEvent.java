package io.github.smiskinext.tenant.domain.event;

import io.github.smiskinext.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * A {@link DomainEvent} intended for external publishing via Kafka (CloudEvents 1.0).
 *
 * <p>Unlike the meet service's {@code PublishableEvent} which uses UUID aggregate IDs, the tenant
 * service uses String (cloudId) as its aggregate identity.
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
