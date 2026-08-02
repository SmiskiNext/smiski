package io.github.smiskinext.notification.infrastructure.email;

import com.svix.Webhook;
import com.svix.exceptions.WebhookVerificationException;
import io.github.smiskinext.notification.domain.port.WebhookVerifier;
import java.net.http.HttpHeaders;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Svix-based webhook signature verification.
 */
@Component
@EnableConfigurationProperties(WebhookProperties.class)
public class SvixWebhookVerifier implements WebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(SvixWebhookVerifier.class);

    private final Webhook webhook;

    public SvixWebhookVerifier(WebhookProperties properties) {
        this.webhook = new Webhook(properties.getSigningSecret());
    }

    @Override
    public boolean verify(String payload, Map<String, String> headers) {
        try {
            HttpHeaders httpHeaders = HttpHeaders.of(
                    Map.of(
                            "svix-id", java.util.List.of(headers.getOrDefault("svix-id", "")),
                            "svix-timestamp",
                                    java.util.List.of(headers.getOrDefault("svix-timestamp", "")),
                            "svix-signature",
                                    java.util.List.of(headers.getOrDefault("svix-signature", ""))),
                    (name, value) -> true);
            webhook.verify(payload, httpHeaders);
            return true;
        } catch (WebhookVerificationException e) {
            log.warn("Inbound webhook rejected: invalid Svix signature");
            return false;
        }
    }
}
