package io.github.smiskinext.tenant.application.command;

import io.github.smiskinext.shared.application.Command;

public record UninstallTenantCommand(String cloudId) implements Command {}
