package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Framework-agnostic representation of a decoded and signature-verified LiveKit webhook event.
 *
 * <p>Carries only the fields the meet service acts on. The {@code roomMetadata} holds the owning
 * tenant identifier embedded at token-issue time, letting the consumer resolve the tenant without a
 * request header. Participant fields are absent for room-lifecycle events.
 *
 * @param eventType           the LiveKit event name (e.g. {@code room_started}, {@code participant_joined})
 * @param roomName            the LiveKit room name of the form {@code meeting-<meetingId>}
 * @param roomMetadata        the room metadata carrying the owning tenant identifier
 * @param participantIdentity the participant identity of the form {@code <accountId>:<deviceId>}
 * @param participantSid      the LiveKit participant session id
 * @param participantAttributes participant attributes (carries the participant role)
 * @param webhookId           the LiveKit webhook delivery id
 * @param occurredAt          the event creation time
 */
public record LiveKitWebhookEvent(
        String eventType,
        @Nullable String roomName,
        @Nullable String roomMetadata,
        @Nullable String participantIdentity,
        @Nullable String participantSid,
        Map<String, String> participantAttributes,
        String webhookId,
        Instant occurredAt)
        implements ValueObject {

    public LiveKitWebhookEvent {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(webhookId, "webhookId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        participantAttributes =
                participantAttributes == null ? Map.of() : Map.copyOf(participantAttributes);
    }

    public Optional<String> tenantMetadata() {
        return Optional.ofNullable(roomMetadata).filter(value -> !value.isBlank());
    }
}
