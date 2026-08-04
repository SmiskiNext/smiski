package io.github.smiskinext.shared.infrastructure.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @Valid @NotNull Relay relay,
        @Valid @NotNull Cloudevent cloudevent,
        @NotBlank String transport) {

    public OutboxProperties {
        if (relay == null) {
            relay = new Relay(true, Duration.ofSeconds(5), 50);
        }
        if (cloudevent == null) {
            cloudevent = new Cloudevent("smiski");
        }
        if (transport == null || transport.isBlank()) {
            transport = "kafka";
        }
    }

    public record Relay(
            boolean enabled,
            @NotNull Duration fixedDelay,
            @Min(1) int batchSize) {

        public Relay {
            if (fixedDelay == null) {
                fixedDelay = Duration.ofSeconds(5);
            }
            if (batchSize < 1) {
                batchSize = 50;
            }
        }
    }

    public record Cloudevent(@NotBlank String source) {

        public Cloudevent {
            if (source == null || source.isBlank()) {
                source = "smiski";
            }
        }
    }
}
