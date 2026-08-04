package io.github.smiskinext.meet.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the no-show meeting canceler, bound from {@code app.meet.no-show-canceler}.
 *
 * <p>The batch size caps how many expired meetings a single run cancels. The fixed delay is the
 * pause between consecutive runs and must be strictly positive so the scheduler always advances.
 *
 * @param batchSize maximum meetings canceled per run; must be at least 1
 * @param fixedDelay pause between runs; must be strictly positive
 */
@Validated
@ConfigurationProperties(prefix = "app.meet.no-show-canceler")
public record NoShowCancelerProperties(
        @Min(1) int batchSize, @NotNull Duration fixedDelay) {

    private static final int BUILT_IN_BATCH_SIZE = 100;
    private static final Duration BUILT_IN_FIXED_DELAY = Duration.ofMinutes(5);

    public NoShowCancelerProperties {
        if (batchSize < 1) {
            batchSize = BUILT_IN_BATCH_SIZE;
        }
        if (fixedDelay == null) {
            fixedDelay = BUILT_IN_FIXED_DELAY;
        }
        if (fixedDelay.isNegative() || fixedDelay.isZero()) {
            throw new IllegalArgumentException(
                    "app.meet.no-show-canceler.fixed-delay must be strictly positive");
        }
    }

    public NoShowCancelerProperties() {
        this(BUILT_IN_BATCH_SIZE, BUILT_IN_FIXED_DELAY);
    }
}
