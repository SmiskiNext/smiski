package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.PublishableEvent;

/**
 * Port for publishing domain events to the external message broker (Kafka).
 */
public interface EventPublisher {

    void publish(PublishableEvent event);
}
