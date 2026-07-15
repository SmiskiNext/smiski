package io.github.smiskinext.shared.domain;

/**
 * Port for publishing domain events to the transactional outbox. Services inject this interface to
 * enqueue events for eventual delivery to external consumers.
 */
public interface EventPublisher {

    void publish(PublishableEvent event);
}
