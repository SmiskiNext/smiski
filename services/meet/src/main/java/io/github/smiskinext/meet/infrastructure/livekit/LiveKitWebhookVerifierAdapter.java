package io.github.smiskinext.meet.infrastructure.livekit;

import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;
import io.github.smiskinext.meet.domain.port.LiveKitWebhookVerifier;
import io.livekit.server.WebhookReceiver;
import java.time.Instant;
import java.util.Map;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.Room;
import livekit.LivekitWebhook.WebhookEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies and decodes LiveKit webhooks using the server SDK {@link WebhookReceiver}, mapping the
 * decoded protobuf event into the framework-agnostic {@link LiveKitWebhookEvent}.
 *
 * <p>A missing or cryptographically invalid signature is reported as
 * {@link Verification.Status#INVALID_SIGNATURE}. A validly signed request whose body cannot be
 * decoded into a webhook event is reported as {@link Verification.Status#MALFORMED}.
 */
@Component
public class LiveKitWebhookVerifierAdapter implements LiveKitWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookVerifierAdapter.class);

    private final WebhookReceiver webhookReceiver;

    public LiveKitWebhookVerifierAdapter(WebhookReceiver webhookReceiver) {
        this.webhookReceiver = webhookReceiver;
    }

    @Override
    public Verification verify(String rawBody, @Nullable String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return Verification.invalidSignature();
        }
        WebhookEvent event;
        try {
            event = webhookReceiver.receive(rawBody, authHeader);
        } catch (Exception e) {
            log.debug("LiveKit webhook signature verification failed: {}", e.getMessage());
            return Verification.invalidSignature();
        }
        if (event == null || event.getEvent().isBlank()) {
            return Verification.malformed();
        }
        return Verification.valid(toDomain(event));
    }

    private LiveKitWebhookEvent toDomain(WebhookEvent event) {
        Room room = event.hasRoom() ? event.getRoom() : null;
        ParticipantInfo participant = event.hasParticipant() ? event.getParticipant() : null;
        return new LiveKitWebhookEvent(
                event.getEvent(),
                room != null ? room.getName() : null,
                room != null ? room.getMetadata() : null,
                participant != null ? participant.getIdentity() : null,
                participant != null ? participant.getSid() : null,
                participant != null ? participant.getAttributesMap() : Map.of(),
                event.getId(),
                Instant.ofEpochSecond(event.getCreatedAt()));
    }
}
