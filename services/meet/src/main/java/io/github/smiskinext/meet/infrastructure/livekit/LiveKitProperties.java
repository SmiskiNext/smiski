package io.github.smiskinext.meet.infrastructure.livekit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LiveKit server configuration bound from the {@code app.livekit} prefix.
 *
 * @param url LiveKit server HTTP URL
 * @param wsUrl LiveKit server WebSocket URL
 * @param apiKey LiveKit API key
 * @param apiSecret LiveKit API secret
 * @param webhookTopic internal Kafka topic that verified LiveKit webhook events are published to;
 *     must not be blank
 * @param tokenExpirySeconds access-token time-to-live in seconds; must be positive
 */
@Validated
@ConfigurationProperties(prefix = "app.livekit")
public record LiveKitProperties(
        @NotBlank String url,
        @NotBlank String wsUrl,
        @NotBlank String apiKey,
        @NotBlank String apiSecret,
        @NotBlank String webhookTopic,
        @Positive int tokenExpirySeconds) {

    public LiveKitProperties {
        if (tokenExpirySeconds == 0) {
            tokenExpirySeconds = 1800;
        }
    }
}
