package io.github.smiskinext.usermanagement.infrastructure.messaging;

import io.github.smiskinext.usermanagement.domain.EventPublisher;
import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link EventPublisher} that logs a warning and discards the event. Active when no
 * {@link KafkaEventPublisher} bean is present (e.g., during unit tests without Kafka).
 */
@Component
@ConditionalOnMissingBean(EventPublisher.class)
public class NoOpEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventPublisher.class);

    @Override
    public void publish(PublishableEvent event) {
        log.warn(
                "NoOpEventPublisher: event discarded (no Kafka publisher configured). "
                        + "type={}, aggregateId={}, eventId={}",
                event.eventType(),
                event.aggregateId(),
                event.eventId());
    }
}
