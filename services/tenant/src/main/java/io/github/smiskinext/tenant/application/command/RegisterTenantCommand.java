package io.github.smiskinext.tenant.application.command;

import io.github.smiskinext.shared.application.Command;

import org.jspecify.annotations.Nullable;

public record RegisterTenantCommand(
        String cloudId,
        String installationId,
        String appId,
        @Nullable String appVersion,
        @Nullable String environmentId,
        @Nullable String siteUrl,
        @Nullable String installerAccountId)
        implements Command {}
