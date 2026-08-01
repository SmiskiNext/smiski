package io.github.smiskinext.notification.infrastructure.email;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Resend inbound webhook configuration.
 *
 * <p>Bound from {@code app.notification.webhook.*}. Validation fails startup when the signing
 * secret is absent so a misconfigured deployment cannot process unverified inbound mail.
 */
@Validated
@ConfigurationProperties(prefix = "app.notification.webhook")
public class WebhookProperties {

    @NotBlank private String signingSecret = "";

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }
}
