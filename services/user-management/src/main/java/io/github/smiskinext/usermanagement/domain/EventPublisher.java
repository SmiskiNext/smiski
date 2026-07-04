package io.github.smiskinext.usermanagement.domain;

import java.util.List;

/**
 * Port interface for publishing {@link PublishableEvent}s externally (e.g., to Kafka).
 */
public interface EventPublisher {

    void publish(PublishableEvent event);

    default void publishAll(List<PublishableEvent> events) {
        events.forEach(this::publish);
    }
}
