package io.github.smiskinext.shared.infrastructure.outbox;

import com.google.protobuf.Message;

import io.github.smiskinext.shared.domain.PublishableEvent;
import io.github.smiskinext.shared.infrastructure.outbox.OutboxStore.NewOutboxEvent;

/**
 * Shared implementation of {@link EventPublisher} that resolves a proto mapper, encodes the domain
 * event as a CloudEvent, and appends it to the transactional outbox via {@link OutboxStore}.
 */
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxStore outboxStore;
    private final OutboxEventProtoMapperRegistry mapperRegistry;
    private final CloudEventEncoder cloudEventEncoder;

    public OutboxEventPublisher(
            OutboxStore outboxStore,
            OutboxEventProtoMapperRegistry mapperRegistry,
            CloudEventEncoder cloudEventEncoder) {
        this.outboxStore = outboxStore;
        this.mapperRegistry = mapperRegistry;
        this.cloudEventEncoder = cloudEventEncoder;
    }

    @Override
    public void publish(PublishableEvent event) {
        OutboxEventProtoMapper<PublishableEvent> mapper = resolveMapper(event);
        Message protoMessage = mapper.toProto(event);

        String payload = cloudEventEncoder.encode(
                event.eventId(),
                event.eventType(),
                mapper.dataSchema(),
                event.occurredAt(),
                event.aggregateId(),
                protoMessage);

        outboxStore.append(new NewOutboxEvent(
                event.eventId(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.topic(),
                payload,
                event.occurredAt()));
    }

    @SuppressWarnings("unchecked")
    private OutboxEventProtoMapper<PublishableEvent> resolveMapper(PublishableEvent event) {
        Class<PublishableEvent> eventClass = (Class<PublishableEvent>) event.getClass();
        return mapperRegistry.resolve(eventClass);
    }
}
