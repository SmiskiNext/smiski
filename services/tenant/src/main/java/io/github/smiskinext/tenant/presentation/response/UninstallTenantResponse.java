package io.github.smiskinext.tenant.presentation.response;

import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.domain.model.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Schema(description = "Tenant uninstall result")
public record UninstallTenantResponse(
        @Schema(description = "Installation identifier", example = "install-xyz-456")
        String installationId,

        @Schema(description = "Application identifier", example = "app-1")
        String appId,

        @Schema(description = "Application version", example = "1.0.0", nullable = true) @Nullable String appVersion,

        @Schema(description = "Environment identifier", example = "env-1", nullable = true)
        @Nullable String environmentId,

        @Schema(
                description = "Atlassian site URL",
                example = "https://example.atlassian.net",
                nullable = true)
        @Nullable String siteUrl,

        @Schema(
                description = "Account ID of the installer",
                example = "installer-1",
                nullable = true)
        @Nullable String installerAccountId,

        @Schema(description = "Current tenant status", example = "UNINSTALLED")
        TenantStatus status,

        @Schema(description = "Timestamp of initial installation", example = "2025-01-15T10:30:00Z")
        Instant installedAt,

        @Schema(description = "Timestamp of last update", example = "2025-06-15T10:30:00Z")
        Instant updatedAt,

        @Schema(
                description = "Timestamp when the tenant was uninstalled",
                example = "2025-06-15T10:30:00Z",
                nullable = true)
        @Nullable Instant uninstalledAt,

        @Schema(
                description = "Timestamp after which tenant data may be purged",
                example = "2025-07-15T10:30:00Z",
                nullable = true)
        @Nullable Instant purgeAfter) {

    public static UninstallTenantResponse from(UninstallTenantResult result) {
        return new UninstallTenantResponse(
                result.installationId(),
                result.appId(),
                result.appVersion(),
                result.environmentId(),
                result.siteUrl(),
                result.installerAccountId(),
                result.status(),
                result.installedAt(),
                result.updatedAt(),
                result.uninstalledAt(),
                result.purgeAfter());
    }
}
