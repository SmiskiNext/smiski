package io.github.smiskinext.notification.infrastructure.email;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Resend email provider configuration.
 *
 * <p>Bound from {@code app.notification.email.*}. Validation fails application startup when the API
 * key is absent or blank, so a misconfigured deployment cannot silently drop calendar mail.
 */
@Validated
@ConfigurationProperties(prefix = "app.notification.email")
public class EmailProperties {

    /** Resend API key used to authenticate outgoing calendar mail. Must not be blank. */
    @NotBlank private String apiKey = "";

    /** Sender address applied to every outgoing calendar email. Must not be blank. */
    @NotBlank private String sender = "";

    /**
     * IETF BCP 47 language tag for the default email locale.
     *
     * <p>All message bundle lookups use this locale. Falls back to English when the locale is not
     * supported by the bundle.
     */
    private String defaultLocale = "en";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
