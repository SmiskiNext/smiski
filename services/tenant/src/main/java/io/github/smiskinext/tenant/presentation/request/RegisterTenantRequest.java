package io.github.smiskinext.tenant.presentation.request;

import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

public record RegisterTenantRequest(
        @NotBlank String id,
        @Nullable String installerAccountId,
        @NotNull @Valid App app,
        @Nullable Environment environment,
        @Nullable String environmentType,
        @Nullable String siteUrl) {

    public record App(
            @NotBlank String id,
            @NotNull String version,
            @Nullable String name,
            @Nullable String ownerAccountId) {}

    public record Environment(@Nullable String id) {}

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
