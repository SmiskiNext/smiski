package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;

/**
 * Port for publishing a verified LiveKit webhook event onto the internal processing channel.
 *
 * <p>Events are keyed by room name so that all events for a single room preserve their relative
 * ordering on one partition. Publishing decouples the fast signature-verification acknowledgement
 * from the database work performed by the consumer.
 */
public interface LiveKitWebhookPublisher {

    void publish(LiveKitWebhookEvent event);
}
