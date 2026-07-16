package io.github.smiskinext.shared.infrastructure.outbox;

import static org.mockito.Mockito.*;

import io.github.smiskinext.shared.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDomainEventListenerTest {

    @Test
    void publishable_event_is_forwarded_to_outbox_publisher() {
        OutboxEventPublisher outboxPublisher = mock(OutboxEventPublisher.class);
        OutboxDomainEventListener listener = new OutboxDomainEventListener(outboxPublisher);

        PublishableEvent event = new TestPublishableEvent();

        listener.onPublishableEvent(event);

        verify(outboxPublisher).publish(event);
    }

    @Test
    void listener_method_only_accepts_publishable_events() {
        OutboxEventPublisher outboxPublisher = mock(OutboxEventPublisher.class);
        new OutboxDomainEventListener(outboxPublisher);

        verifyNoInteractions(outboxPublisher);
    }

    private static final class TestPublishableEvent implements PublishableEvent {

        @Override
        public UUID eventId() {
            return UUID.randomUUID();
        }

        @Override
        public String aggregateId() {
            return "agg-1";
        }

        @Override
        public String aggregateType() {
            return "TestAggregate";
        }

        @Override
        public String eventType() {
            return "test.event.created";
        }

        @Override
        public String topic() {
            return "test-topic";
        }

        @Override
        public Instant occurredAt() {
            return Instant.now();
        }
    }
}
