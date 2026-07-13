package io.github.smiskinext.tenant.application.usecase;

import io.github.smiskinext.shared.application.UseCase;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.domain.TenantError;

public interface RegisterTenantUseCase
        extends UseCase<RegisterTenantCommand, TenantResponse, TenantError> {}
