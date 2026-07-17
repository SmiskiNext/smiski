package io.github.smiskinext.shared.infrastructure.event;

import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.DomainEvent;
import io.github.smiskinext.shared.domain.EventPublisher;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Infrastructure implementation of {@link EventPublisher} that forwards each registered domain
 * event on the aggregate to Spring's {@link ApplicationEventPublisher}, then clears the aggregate's
 * event list.
 */
public class SpringDomainEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishEventsOf(AggregateRoot<?> aggregate) {
        for (DomainEvent event : aggregate.getDomainEvents()) {
            applicationEventPublisher.publishEvent(event);
        }
        aggregate.clearDomainEvents();
    }
}
