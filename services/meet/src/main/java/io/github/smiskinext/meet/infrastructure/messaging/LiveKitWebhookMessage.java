package io.github.smiskinext.meet.infrastructure.messaging;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Internal Kafka message carrying a verified LiveKit webhook event to the processing consumer.
 * Serialized as JSON and keyed by room name to preserve per-room ordering.
 *
 * @param eventType             the LiveKit event name
 * @param roomName              the LiveKit room name
 * @param roomMetadata          the room metadata carrying the owning tenant identifier
 * @param participantIdentity   the participant identity
 * @param participantSid        the participant session id
 * @param participantAttributes participant attributes
 * @param webhookId             the LiveKit webhook delivery id
 * @param occurredAtEpochSecond the event creation time as epoch seconds
 */
public record LiveKitWebhookMessage(
        String eventType,
        @Nullable String roomName,
        @Nullable String roomMetadata,
        @Nullable String participantIdentity,
        @Nullable String participantSid,
        Map<String, String> participantAttributes,
        String webhookId,
        long occurredAtEpochSecond) {}
