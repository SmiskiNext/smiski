package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.domain.port.InstantMeetingSettings;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for instant meetings, bound from {@code app.meet.instant}.
 *
 * <p>The default duration defines the time range assigned to every instant meeting at creation.
 * It must be strictly positive so that startTime is always before endTime.
 */
@Validated
@ConfigurationProperties(prefix = "app.meet.instant")
public record InstantMeetingProperties(@NotNull Duration defaultDuration)
        implements InstantMeetingSettings {

    private static final Duration BUILT_IN_DEFAULT = Duration.ofHours(1);

    public InstantMeetingProperties {
        if (defaultDuration == null) {
            defaultDuration = BUILT_IN_DEFAULT;
        }
        if (defaultDuration.isNegative() || defaultDuration.isZero()) {
            throw new IllegalArgumentException(
                    "app.meet.instant.default-duration must be strictly positive");
        }
    }

    public InstantMeetingProperties() {
        this(BUILT_IN_DEFAULT);
    }
}
