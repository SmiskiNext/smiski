package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.valueobject.LiveKitWebhookEvent;

import org.jspecify.annotations.Nullable;

/**
 * Port for verifying and decoding a raw LiveKit webhook request.
 *
 * <p>Verification checks the LiveKit signed-JWT {@code Authorization} payload against the raw
 * request body using the configured API key and secret. The raw body must be passed exactly as
 * received, since the signature is a hash of the body bytes.
 */
public interface LiveKitWebhookVerifier {

    /**
     * Outcome of verifying and decoding a webhook request.
     *
     * @param status the verification result
     * @param event  the decoded event when {@code status} is {@link Status#VALID}, otherwise null
     */
    record Verification(Status status, @Nullable LiveKitWebhookEvent event) {

        public enum Status {
            /** Signature valid and body decoded into an event. */
            VALID,
            /** Missing or cryptographically invalid signature. */
            INVALID_SIGNATURE,
            /** Signature valid but the body could not be decoded into a webhook event. */
            MALFORMED
        }

        public static Verification valid(LiveKitWebhookEvent event) {
            return new Verification(Status.VALID, event);
        }

        public static Verification invalidSignature() {
            return new Verification(Status.INVALID_SIGNATURE, null);
        }

        public static Verification malformed() {
            return new Verification(Status.MALFORMED, null);
        }
    }

    /**
     * Verifies the signature and decodes the body.
     *
     * @param rawBody    the raw request body exactly as received
     * @param authHeader the {@code Authorization} header value, may be null
     * @return the verification outcome
     */
    Verification verify(String rawBody, @Nullable String authHeader);
}
