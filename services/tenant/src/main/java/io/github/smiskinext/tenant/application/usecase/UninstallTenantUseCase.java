package io.github.smiskinext.tenant.application.usecase;

import io.github.smiskinext.shared.application.UseCase;
import io.github.smiskinext.tenant.application.command.UninstallTenantCommand;
import io.github.smiskinext.tenant.application.result.UninstallTenantResult;
import io.github.smiskinext.tenant.domain.TenantError;

public interface UninstallTenantUseCase
        extends UseCase<UninstallTenantCommand, UninstallTenantResult, TenantError> {}
