package io.github.smiskinext.meet.application.usecase;

import org.jspecify.annotations.Nullable;

/**
 * Inbound port for receiving a raw LiveKit webhook at the HTTP boundary. Verifies the signature
 * synchronously and, on success, enqueues the decoded event for asynchronous processing before
 * acknowledging.
 */
public interface ReceiveLiveKitWebhookUseCase {

    /**
     * Acknowledgement outcome mapped to an HTTP status by the controller.
     */
    enum Acknowledgement {
        /** Signature valid, event enqueued; respond 200. */
        ACCEPTED,
        /** Missing or invalid signature; respond 401 with no side effects. */
        INVALID_SIGNATURE,
        /** Signature valid but body undecodable; respond client error with no side effects. */
        MALFORMED
    }

    /**
     * Verifies and enqueues a webhook.
     *
     * @param rawBody    the raw request body exactly as received
     * @param authHeader the {@code Authorization} header value, may be null
     * @return the acknowledgement outcome
     */
    Acknowledgement receive(String rawBody, @Nullable String authHeader);
}
