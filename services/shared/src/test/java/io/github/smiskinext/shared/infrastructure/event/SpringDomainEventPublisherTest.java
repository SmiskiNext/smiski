package io.github.smiskinext.shared.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.smiskinext.shared.domain.AggregateRoot;
import io.github.smiskinext.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringDomainEventPublisherTest {

    @Test
    void publishEventsOf_forwards_each_event_and_clears_aggregate() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(springPublisher);

        TestAggregate aggregate = new TestAggregate();
        DomainEvent event1 = new TestEvent(Instant.now());
        DomainEvent event2 = new TestEvent(Instant.now());
        aggregate.addEvent(event1);
        aggregate.addEvent(event2);

        publisher.publishEventsOf(aggregate);

        verify(springPublisher).publishEvent(event1);
        verify(springPublisher).publishEvent(event2);
        verifyNoMoreInteractions(springPublisher);
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void publishEventsOf_does_nothing_when_no_events_registered() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(springPublisher);

        TestAggregate aggregate = new TestAggregate();

        publisher.publishEventsOf(aggregate);

        verifyNoInteractions(springPublisher);
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    private static class TestAggregate extends AggregateRoot<UUID> {

        private final UUID id = UUID.randomUUID();

        @Override
        public UUID getId() {
            return id;
        }

        void addEvent(DomainEvent event) {
            registerEvent(event);
        }
    }

    private record TestEvent(Instant occurredAt) implements DomainEvent {}
}
