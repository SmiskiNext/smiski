package io.github.smiskinext.tenant.presentation.request;

import io.github.smiskinext.tenant.application.command.UninstallTenantCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

@Schema(description = "Forge preUninstall life-cycle event payload")
public record UninstallTenantRequest(
        @Schema(description = "Installation identifier", example = "install-xyz-456") @Nullable String id,

        @Schema(description = "Application metadata", nullable = true) @Nullable App app) {

    @Schema(description = "Application metadata for the uninstall event")
    public record App(
            @Schema(description = "Application identifier", example = "app-1") @Nullable String id,

            @Schema(description = "Application version", example = "1.0.0", nullable = true)
            @Nullable String version) {}

    public UninstallTenantCommand toCommand(String cloudId) {
        return new UninstallTenantCommand(cloudId);
    }
}
