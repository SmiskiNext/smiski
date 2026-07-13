package io.github.smiskinext.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TenantInstalledEvent(
        UUID eventId,
        String aggregateId,
        String installationId,
        String appId,
        @Nullable String appVersion,
        @Nullable String environmentId,
        String environmentType,
        @Nullable String siteUrl,
        @Nullable String installerAccountId,
        Instant installedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "tenant";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.tenant.v1.installed";
    }

    @Override
    public String topic() {
        return "tenant.tenant.installed";
    }

    @Override
    public Instant occurredAt() {
        return installedAt;
    }
}
