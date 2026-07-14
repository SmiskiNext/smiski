package io.github.smiskinext.tenant.presentation.request;

import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;
import io.github.smiskinext.tenant.presentation.validation.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

@Schema(description = "Request body for registering a tenant installation")
public record RegisterTenantRequest(
        @Schema(description = "Unique installation identifier", example = "install-xyz-456")
        @NotBlank String id,

        @Schema(
                description = "Account ID of the user who installed the app",
                example = "account-abc-123",
                nullable = true)
        @Nullable String installerAccountId,

        @NotNull @Valid App app,
        @Schema(nullable = true) @Nullable Environment environment,

        @Schema(
                description = "Target environment type. Case-insensitive. Defaults to PRODUCTION"
                        + " when absent or blank.",
                example = "PRODUCTION",
                nullable = true)
        @ValidEnum(EnvironmentType.class)
        @Nullable String environmentType,

        @Schema(
                description = "Base URL of the Atlassian site",
                example = "https://mysite.atlassian.net",
                nullable = true)
        @Nullable String siteUrl) {

    @Schema(description = "Application metadata for the installation")
    public record App(
            @Schema(description = "Unique application identifier", example = "app-1") @NotBlank String id,

            @Schema(description = "Application version", example = "1.0.0") @NotNull String version,

            @Schema(
                    description = "Human-readable application name",
                    example = "My App",
                    nullable = true)
            @Nullable String name,

            @Schema(
                    description = "Account ID of the application owner",
                    example = "owner-abc-123",
                    nullable = true)
            @Nullable String ownerAccountId) {}

    @Schema(description = "Environment context for the installation")
    public record Environment(
            @Schema(description = "Environment identifier", example = "env-prod-1", nullable = true)
            @Nullable String id) {}

    public RegisterTenantCommand toCommand(String cloudId) {
        EnvironmentType envType = resolveEnvironmentType();
        String envId = environment != null ? environment.id() : null;
        return new RegisterTenantCommand(
                cloudId, id, app.id(), app.version(), envId, envType, siteUrl, installerAccountId);
    }

    private EnvironmentType resolveEnvironmentType() {
        if (environmentType == null || environmentType.isBlank()) {
            return EnvironmentType.PRODUCTION;
        }
        return EnvironmentType.valueOf(environmentType.toUpperCase());
    }
}
