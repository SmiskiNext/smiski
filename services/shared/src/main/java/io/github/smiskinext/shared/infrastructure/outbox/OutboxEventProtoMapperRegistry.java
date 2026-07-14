package io.github.smiskinext.shared.infrastructure.outbox;

import io.github.smiskinext.shared.domain.PublishableEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry that resolves an {@link OutboxEventProtoMapper} by the runtime class of the domain
 * event. Fails with a clear exception when no mapper is registered for a given event type.
 */
public class OutboxEventProtoMapperRegistry {

    private final Map<Class<?>, OutboxEventProtoMapper<?>> mappers;

    public OutboxEventProtoMapperRegistry(List<OutboxEventProtoMapper<?>> mapperList) {
        this.mappers = mapperList.stream()
                .collect(Collectors.toMap(OutboxEventProtoMapper::eventType, m -> m));
    }

    @SuppressWarnings("unchecked")
    public <E extends PublishableEvent> OutboxEventProtoMapper<E> resolve(Class<E> eventType) {
        OutboxEventProtoMapper<?> mapper = mappers.get(eventType);
        if (mapper == null) {
            throw new IllegalArgumentException(
                    "No OutboxEventProtoMapper registered for event type: " + eventType.getName());
        }
        return (OutboxEventProtoMapper<E>) mapper;
    }
}
