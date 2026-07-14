package io.github.smiskinext.shared.infrastructure.outbox;

import io.cloudevents.CloudEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Transport abstraction for publishing CloudEvents from the outbox relay.
 *
 * <p>Implementations are selected by configuration (e.g. Kafka, EventBridge).
 */
public interface OutboxTransport {

    CompletableFuture<Void> send(String topic, String key, CloudEvent event);
}
