package io.github.smiskinext.shared.infrastructure.outbox;

import com.google.protobuf.Message;

import io.github.smiskinext.shared.domain.PublishableEvent;

/**
 * Maps a domain event to its corresponding Protocol Buffer message for outbox storage. Each event
 * type has its own mapper bean registered in the application context.
 *
 * @param <E> the concrete publishable event type
 */
public interface OutboxEventProtoMapper<E extends PublishableEvent> {

    Class<E> eventType();

    String dataSchema();

    Message toProto(E event);
}
