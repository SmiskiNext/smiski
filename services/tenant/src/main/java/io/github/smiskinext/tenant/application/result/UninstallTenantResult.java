package io.github.smiskinext.tenant.application.result;

import io.github.smiskinext.tenant.domain.model.TenantStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record UninstallTenantResult(
        String tenantId,
        String installationId,
        String appId,
        @Nullable String appVersion,
        @Nullable String environmentId,
        @Nullable String siteUrl,
        @Nullable String installerAccountId,
        TenantStatus status,
        Instant installedAt,
        Instant updatedAt,
        @Nullable Instant uninstalledAt,
        @Nullable Instant purgeAfter) {}
