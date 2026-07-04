package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.PublishableEvent;

/**
 * Port for publishing domain events to the external message broker (Kafka).
 */
public interface EventPublisher {

    void publish(PublishableEvent event);
}
