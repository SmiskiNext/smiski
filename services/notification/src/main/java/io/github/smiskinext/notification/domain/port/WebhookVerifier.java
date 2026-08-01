package io.github.smiskinext.notification.domain.port;

import java.util.Map;

/**
 * Port for verifying inbound webhook signatures.
 */
public interface WebhookVerifier {

    /**
     * Verifies the webhook payload against the provided headers.
     *
     * @return true if the signature is valid, false otherwise
     */
    boolean verify(String payload, Map<String, String> headers);
}
