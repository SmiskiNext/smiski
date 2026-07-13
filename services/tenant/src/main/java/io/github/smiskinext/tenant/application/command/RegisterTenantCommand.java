package io.github.smiskinext.tenant.application.command;

import io.github.smiskinext.shared.application.Command;
import io.github.smiskinext.tenant.domain.model.EnvironmentType;

import org.jspecify.annotations.Nullable;

public record RegisterTenantCommand(
        String cloudId,
        String installationId,
        String appId,
        @Nullable String appVersion,
        @Nullable String environmentId,
        EnvironmentType environmentType,
        @Nullable String siteUrl,
        @Nullable String installerAccountId)
        implements Command {}
