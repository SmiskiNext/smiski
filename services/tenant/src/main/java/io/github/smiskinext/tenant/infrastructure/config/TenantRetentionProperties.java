package io.github.smiskinext.tenant.infrastructure.config;

import io.github.smiskinext.tenant.domain.port.PurgePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.tenant.retention")
public record TenantRetentionProperties(Duration purgeAfter) implements PurgePolicy {

    public TenantRetentionProperties {
        if (purgeAfter == null) {
            purgeAfter = Duration.ofDays(30);
        }
    }

    @Override
    public Duration retention() {
        return purgeAfter;
    }
}
