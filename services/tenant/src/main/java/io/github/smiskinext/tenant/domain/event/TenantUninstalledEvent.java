package io.github.smiskinext.tenant.domain.event;

import io.github.smiskinext.tenant.domain.model.TenantStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TenantUninstalledEvent(
        UUID eventId,
        String aggregateId,
        String installationId,
        String appId,
        Instant uninstalledAt,
        Instant purgeAfter,
        @Nullable String appVersion,
        @Nullable String environmentId,
        @Nullable String siteUrl,
        @Nullable String installerAccountId,
        TenantStatus status,
        Instant installedAt,
        Instant updatedAt)
        implements PublishableEvent {

    @Override
    public String aggregateType() {
        return "tenant";
    }

    @Override
    public String eventType() {
        return "io.github.smiskinext.tenant.v1.uninstalled";
    }

    @Override
    public String topic() {
        return "tenant.tenant.uninstalled";
    }

    @Override
    public Instant occurredAt() {
        return uninstalledAt;
    }
}
